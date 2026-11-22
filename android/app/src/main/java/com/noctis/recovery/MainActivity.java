package com.noctis.recovery;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/** Preview shell. Local UI is served under the configured trusted HTTPS origin. */
public final class MainActivity extends Activity {
 private static final String PREFS="noctis_settings", KEY_ALIAS="noctis_preview_pin_v3";
 private static final int LOCATION_REQUEST=100, NOTIFICATION_REQUEST=101;
 private static final int BG=Color.rgb(5,5,8), CYAN=Color.rgb(0,240,255), WHITE=Color.rgb(235,248,250);
 private volatile String origin; private WebView web; private FrameLayout outer; private LinearLayout root,lockView; private TextView lockHelp; private EditText pinInput; private Button pinButton;
 private boolean unlocked=false, loaded=false; private String pendingPin=null; private int previousVolume=-1;
 private GeolocationPermissions.Callback pending; private String pendingOrigin;
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  origin=getPreferencesValue();outer=new FrameLayout(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
  LinearLayout bar=new LinearLayout(this);bar.setPadding(dp(14),dp(6),dp(14),dp(6));bar.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=text("NOCTIS  /  3.0",14,CYAN);title.setLetterSpacing(.12f);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button settings=new Button(this);settings.setText("SERVER ⚙");settings.setTextColor(WHITE);settings.setBackgroundColor(Color.rgb(24,45,54));settings.setOnClickListener(v->settings());bar.addView(settings);root.addView(bar);
  web=new WebView(this);web.setBackgroundColor(BG);WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setGeolocationEnabled(true);ws.setMediaPlaybackRequiresUserGesture(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    Uri u=request.getUrl();if(!"GET".equals(request.getMethod())||!sameOrigin(u))return null;String p=u.getPath();if(p==null)return null;
    Map<String,String> files=new HashMap<>();files.put("/","index.html");files.put("/device","device.html");files.put("/app.js","app.js");files.put("/device.js","device.js");files.put("/v3.js","v3.js");files.put("/map-init.js","map-init.js");files.put("/style.css","style.css");files.put("/radar.jpg","radar.jpg");files.put("/manifest.webmanifest","manifest.webmanifest");files.put("/sw.js","sw.js");files.put("/icon.svg","icon.svg");files.put("/vendor/maplibre-gl.mjs","vendor/maplibre-gl.mjs");files.put("/vendor/maplibre-gl-worker.mjs","vendor/maplibre-gl-worker.mjs");files.put("/vendor/maplibre-gl.css","vendor/maplibre-gl.css");
    String file=files.get(p);if(file==null)return null;try{InputStream in=getAssets().open("web/"+file);String mime=file.endsWith(".html")?"text/html":(file.endsWith(".js")||file.endsWith(".mjs"))?"text/javascript":file.endsWith(".css")?"text/css":file.endsWith(".jpg")?"image/jpeg":file.endsWith(".svg")?"image/svg+xml":"application/manifest+json";
     Map<String,String> h=new HashMap<>();h.put("Cache-Control","no-store");h.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://tiles.openfreemap.org; connect-src 'self' wss: https://tiles.openfreemap.org; worker-src 'self' blob:; frame-src 'none'; font-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");return new WebResourceResponse(mime,"UTF-8",200,"OK",h,in);
    }catch(Exception e){return null;}
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){Uri u=request.getUrl();if(sameOrigin(u))return false;if("https".equalsIgnoreCase(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
  });
  web.addJavascriptInterface(new AlarmBridge(),"NoctisNative");web.setWebChromeClient(new WebChromeClient(){@Override public void onGeolocationPermissionsShowPrompt(String requestOrigin,GeolocationPermissions.Callback callback){if(!sameOrigin(Uri.parse(requestOrigin))){callback.invoke(requestOrigin,false,false);return;}if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(requestOrigin,true,false);return;}pending=callback;pendingOrigin=requestOrigin;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));outer.addView(root);root.setVisibility(View.INVISIBLE);makeLock();outer.addView(lockView);setContentView(outer);createNotificationChannel();
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);} private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 private void makeLock(){lockView=new LinearLayout(this);lockView.setOrientation(LinearLayout.VERTICAL);lockView.setPadding(dp(28),dp(28),dp(28),dp(28));lockView.setGravity(Gravity.CENTER);lockView.setBackgroundColor(BG);TextView brand=text("NOCTIS / 3.0",30,CYAN);brand.setLetterSpacing(.16f);lockView.addView(brand);TextView head=text("Privacy lock",25,WHITE);head.setPadding(0,dp(28),0,dp(10));lockView.addView(head);lockHelp=text("",14,Color.rgb(170,195,202));lockView.addView(lockHelp);pinInput=new EditText(this);pinInput.setSingleLine(true);pinInput.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pinInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});pinInput.setTextColor(WHITE);pinInput.setHintTextColor(Color.LTGRAY);pinInput.setHint("4-digit PIN");lockView.addView(pinInput,new LinearLayout.LayoutParams(-1,dp(56)));pinButton=new Button(this);pinButton.setText("Continue");pinButton.setTextColor(BG);pinButton.setBackgroundColor(CYAN);pinButton.setOnClickListener(v->checkPin());lockView.addView(pinButton,new LinearLayout.LayoutParams(-1,dp(52)));TextView note=text("This lock protects the app on this device only. Forgotten PIN requires clearing app data and re-pairing.",13,Color.rgb(160,185,190));note.setPadding(0,dp(24),0,0);lockView.addView(note);TextView credit=text("System Developed by Hafiz Faizan Murtaza",12,Color.rgb(117,197,207));credit.setPadding(0,dp(36),0,0);lockView.addView(credit);updateLockText();}
 private void updateLockText(){boolean exists=getPreferences(PREFS).contains("pin_verifier");lockHelp.setText(exists?"Enter your PIN to unlock.":pendingPin==null?"Create a four-digit PIN.":"Enter the same PIN again to confirm.");}
 private android.content.SharedPreferences getPreferences(String name){return getSharedPreferences(name,MODE_PRIVATE);}
 private byte[] verifier(String pin)throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(KEY_ALIAS)){KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore");kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setDigests(KeyProperties.DIGEST_SHA256).build());kg.generateKey();}SecretKey key=(SecretKey)ks.getKey(KEY_ALIAS,null);Mac m=Mac.getInstance("HmacSHA256");m.init(key);return m.doFinal(("NOCTIS/PIN/v3:"+pin).getBytes(StandardCharsets.UTF_8));}
 private void checkPin(){String pin=pinInput.getText().toString();pinInput.setText("");if(!pin.matches("[0-9]{4}")){lockHelp.setText("Enter exactly four digits.");return;}android.content.SharedPreferences p=getPreferences(PREFS);long until=p.getLong("pin_locked_until",0);if(System.currentTimeMillis()<until){lockHelp.setText("Too many attempts. Wait before trying again.");return;}try{String saved=p.getString("pin_verifier",null);if(saved==null){if(pendingPin==null){pendingPin=pin;updateLockText();return;}if(!pendingPin.equals(pin)){pendingPin=null;lockHelp.setText("PINs did not match. Create your PIN again.");return;}p.edit().putString("pin_verifier",Base64.encodeToString(verifier(pin),Base64.NO_WRAP)).apply();pendingPin=null;}else if(!MessageDigest.isEqual(Base64.decode(saved,Base64.NO_WRAP),verifier(pin))){int attempts=p.getInt("pin_failures",0)+1;android.content.SharedPreferences.Editor e=p.edit();if(attempts>=5){e.putInt("pin_failures",0).putLong("pin_locked_until",System.currentTimeMillis()+30000);lockHelp.setText("Too many attempts. Wait 30 seconds.");}else{e.putInt("pin_failures",attempts);lockHelp.setText("Incorrect PIN. Try again.");}e.apply();return;}p.edit().remove("pin_failures").remove("pin_locked_until").apply();unlocked=true;lockView.setVisibility(View.GONE);root.setVisibility(View.VISIBLE);if(!loaded){loaded=true;web.loadUrl(origin+"/");}}catch(Exception e){lockHelp.setText("Secure PIN storage unavailable. Restart the app.");}}
 private void lock(){unlocked=false;pendingPin=null;if(root!=null)root.setVisibility(View.INVISIBLE);if(lockView!=null){pinInput.setText("");updateLockText();lockView.setVisibility(View.VISIBLE);}}
 private String getPreferencesValue(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("server","https://noctis-faizan-v2.bonto.page");}
 private boolean sameOrigin(Uri u){if(origin==null||origin.isEmpty()||u==null)return false;Uri base=Uri.parse(origin);int a=u.getPort()==-1?443:u.getPort(),b=base.getPort()==-1?443:base.getPort();return "https".equalsIgnoreCase(u.getScheme())&&base.getHost()!=null&&base.getHost().equalsIgnoreCase(u.getHost())&&a==b;}
 private void settings(){if(!unlocked)return;final EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);field.setText(origin);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),0,dp(24),0);box.addView(field);CheckBox volume=new CheckBox(this);volume.setText("Raise media volume during alarm (may be very loud; Android restrictions apply)");volume.setChecked(getPreferences(PREFS).getBoolean("max_alarm",false));box.addView(volume);new AlertDialog.Builder(this).setTitle("Trusted server & privacy").setMessage("HTTPS encrypts data in transit. This preview is not security-audited; server data is not end-to-end encrypted. A local PIN does not protect the web dashboard or encrypt all local app data. Only trust a server you control.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->{try{Uri u=Uri.parse(field.getText().toString().trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(u.getPath()!=null&&!u.getPath().isEmpty()&&!u.getPath().equals("/")))throw new Exception();origin="https://"+u.getHost()+(u.getPort()==-1?"":":"+u.getPort());getPreferences(PREFS).edit().putString("server",origin).putBoolean("max_alarm",volume.isChecked()).apply();web.loadUrl(origin+"/");}catch(Exception e){new AlertDialog.Builder(this).setMessage("Enter a valid HTTPS origin without a path.").setPositiveButton("OK",null).show();}}).show();}
 @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==LOCATION_REQUEST&&pending!=null){boolean ok=false;for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)ok=true;pending.invoke(pendingOrigin,ok,false);pending=null;}}
 private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=new NotificationChannel("noctis_alerts","NOCTIS recovery alerts",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Notifications for recovery commands while connected");c.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);nm.createNotificationChannel(c);}}
 private void showAlert(String action){if(!sameOrigin(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;if(!"alarm".equals(action)&&!"message".equals(action))return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent pi=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(this,"noctis_alerts").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("NOCTIS recovery alert").setContentText("Open NOCTIS to view the alert.").setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pi).build();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(20,n);}
 private void restoreVolume(){if(previousVolume<0)return;AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(am.getStreamVolume(AudioManager.STREAM_MUSIC)==am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))am.setStreamVolume(AudioManager.STREAM_MUSIC,previousVolume,0);previousVolume=-1;}
 private class AlarmBridge {
  @JavascriptInterface public void requestNotifications(){runOnUiThread(()->{if(unlocked&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQUEST);else if(unlocked)Toast.makeText(MainActivity.this,"Check notification settings for NOCTIS",Toast.LENGTH_LONG).show();});}
  @JavascriptInterface public void showAlert(String type){runOnUiThread(()->MainActivity.this.showAlert(type));}
  @JavascriptInterface public void alarmStart(){runOnUiThread(()->{if(!unlocked||!getPreferences(PREFS).getBoolean("max_alarm",false)||!sameOrigin(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(previousVolume<0)previousVolume=am.getStreamVolume(AudioManager.STREAM_MUSIC);try{am.setStreamVolume(AudioManager.STREAM_MUSIC,am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),0);}catch(SecurityException ignored){previousVolume=-1;}});}
  @JavascriptInterface public void alarmStop(){runOnUiThread(()->restoreVolume());}
 }
 @Override protected void onStop(){restoreVolume();lock();super.onStop();}
 @Override protected void onDestroy(){restoreVolume();web.removeJavascriptInterface("NoctisNative");web.destroy();super.onDestroy();}
 @Override public void onBackPressed(){if(!unlocked)return;if(web.canGoBack())web.goBack();else super.onBackPressed();}
}

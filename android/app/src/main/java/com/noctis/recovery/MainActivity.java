package com.noctis.recovery;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.CheckBox;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;

/** WebView client for a trusted, user-provided NOCTIS HTTPS server. */
public final class MainActivity extends Activity {
 private WebView web; private String origin; private int previousVolume=-1; private GeolocationPermissions.Callback pending; private String pendingOrigin;
 private static final String PREFS="noctis_settings"; private static final int LOCATION_REQUEST=100;
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(5,5,8));getWindow().setNavigationBarColor(Color.rgb(5,5,8));
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(5,5,8));
  LinearLayout bar=new LinearLayout(this);bar.setPadding(16,8,16,8);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
  TextView title=new TextView(this);title.setText("NOCTIS  /  RECOVERY");title.setTextColor(Color.rgb(0,240,255));title.setTextSize(14);title.setLetterSpacing(.12f);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button settings=new Button(this);settings.setText("SERVER ⚙");settings.setTextColor(Color.WHITE);settings.setBackgroundColor(Color.rgb(24,45,54));settings.setOnClickListener(v->settings());bar.addView(settings);root.addView(bar);
  web=new WebView(this);web.setBackgroundColor(Color.rgb(5,5,8));WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setGeolocationEnabled(true);ws.setMediaPlaybackRequiresUserGesture(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){Uri u=request.getUrl();if(sameOrigin(u))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}});
  web.addJavascriptInterface(new AlarmBridge(),"NoctisNative");
  web.setWebChromeClient(new WebChromeClient(){@Override public void onGeolocationPermissionsShowPrompt(String requestOrigin,GeolocationPermissions.Callback callback){if(!sameOrigin(Uri.parse(requestOrigin))){callback.invoke(requestOrigin,false,false);return;}if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){callback.invoke(requestOrigin,true,false);return;}pending=callback;pendingOrigin=requestOrigin;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));FrameLayout outer=new FrameLayout(this);outer.addView(root);LinearLayout splash=new LinearLayout(this);splash.setOrientation(LinearLayout.VERTICAL);splash.setGravity(android.view.Gravity.CENTER);splash.setBackgroundColor(Color.rgb(5,5,8));TextView splashTitle=new TextView(this);splashTitle.setText("NOCTIS");splashTitle.setTextSize(40);splashTitle.setLetterSpacing(.2f);splashTitle.setTextColor(Color.rgb(0,240,255));splash.addView(splashTitle);TextView splashCredit=new TextView(this);splashCredit.setText("System Developed by Hafiz Faizan Murtaza");splashCredit.setTextColor(Color.rgb(175,210,215));splashCredit.setTextSize(12);splashCredit.setPadding(0,30,0,0);splash.addView(splashCredit);outer.addView(splash,new FrameLayout.LayoutParams(-1,-1));setContentView(outer);splash.animate().alpha(0).setStartDelay(1200).setDuration(400).withEndAction(()->outer.removeView(splash)).start();
  origin=getPreferencesValue();if(origin.isEmpty()){welcome();}else web.loadUrl(origin+"/");
 }
 private String getPreferencesValue(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("server","");}
 private boolean sameOrigin(Uri u){if(origin==null||origin.isEmpty()||u==null)return false;Uri base=Uri.parse(origin);int a=u.getPort()==-1?443:u.getPort(),b=base.getPort()==-1?443:base.getPort();return "https".equalsIgnoreCase(u.getScheme())&&base.getHost()!=null&&base.getHost().equalsIgnoreCase(u.getHost())&&a==b;}
 private void welcome(){web.loadDataWithBaseURL(null,"<html><body style='background:#050508;color:#e5fdff;font-family:sans-serif;padding:48px 24px'><p style='color:#00f0ff;letter-spacing:5px'>NOCTIS / 2.0</p><h1>Device recovery</h1><p>Connect a trusted HTTPS NOCTIS server using SERVER above. Dashboard and device modes are available after connecting.</p><p style='color:#87b5bd;margin-top:48px;font-size:12px'>System Developed by Hafiz Faizan Murtaza</p></body></html>","text/html","UTF-8",null);}
 private void settings(){final EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);field.setHint("https://your-server.example");field.setText(origin);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,0,24,0);box.addView(field);CheckBox volume=new CheckBox(this);volume.setText("Raise media volume to maximum during alarm (may be very loud; Android restrictions apply)");volume.setChecked(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("max_alarm",false));box.addView(volume);new AlertDialog.Builder(this).setTitle("Trusted server URL").setMessage("Use only your own trusted HTTPS NOCTIS server. This app loads its interface and receives commands from that server.").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w)->{try{Uri u=Uri.parse(field.getText().toString().trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(u.getPath()!=null&&!u.getPath().isEmpty()&&!u.getPath().equals("/")))throw new Exception();origin="https://"+u.getHost()+(u.getPort()==-1?"":":"+u.getPort());getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("server",origin).putBoolean("max_alarm",volume.isChecked()).apply();web.loadUrl(origin+"/");}catch(Exception e){new AlertDialog.Builder(this).setMessage("Enter a valid HTTPS origin without a path.").setPositiveButton("OK",null).show();}}).show();}
 @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){super.onRequestPermissionsResult(req,permissions,results);if(req==LOCATION_REQUEST&&pending!=null){boolean ok=false;for(int result:results)if(result==PackageManager.PERMISSION_GRANTED)ok=true;pending.invoke(pendingOrigin,ok,false);pending=null;}}
 private void restoreVolume(){if(previousVolume<0)return;AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(am.getStreamVolume(AudioManager.STREAM_MUSIC)==am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))am.setStreamVolume(AudioManager.STREAM_MUSIC,previousVolume,0);previousVolume=-1;}
 private class AlarmBridge {
  @JavascriptInterface public void alarmStart(){runOnUiThread(()->{if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("max_alarm",false)||!sameOrigin(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);if(previousVolume<0)previousVolume=am.getStreamVolume(AudioManager.STREAM_MUSIC);try{am.setStreamVolume(AudioManager.STREAM_MUSIC,am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),0);}catch(SecurityException ignored){previousVolume=-1;}});}
  @JavascriptInterface public void alarmStop(){runOnUiThread(()->restoreVolume());}
 }
 @Override protected void onPause(){restoreVolume();super.onPause();}
 @Override protected void onDestroy(){restoreVolume();web.removeJavascriptInterface("NoctisNative");web.destroy();super.onDestroy();}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}

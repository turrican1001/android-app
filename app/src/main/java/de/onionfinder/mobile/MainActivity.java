package de.onionfinder.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
  private static final String DEFAULT = "http://87.106.47.83/onionfinder/";
  private static final Pattern ONION = Pattern.compile("[a-z2-7]{56}\\.onion", Pattern.CASE_INSENSITIVE);
  private String server = DEFAULT;
  private String type = "web";
  private WebView page;
  private EditText search;
  private TextView status;
  private boolean failed;

  private TextView label(String s, int sp, int color) {
    TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
    v.setGravity(Gravity.CENTER_VERTICAL); return v;
  }
  private Button button(String s, Runnable r) {
    Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false);
    b.setOnClickListener(v -> r.run()); return b;
  }
  private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    server = validServer(getPreferences(MODE_PRIVATE).getString("server",DEFAULT));
    if(server==null) server=DEFAULT;
    getWindow().setStatusBarColor(Color.rgb(9,17,29));
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(10,6,10,0);
    root.setBackgroundColor(Color.rgb(12,22,35));
    setContentView(root);
    LinearLayout header=new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=label("OnionFinder",23,Color.rgb(94,223,168));
    header.addView(title,new LinearLayout.LayoutParams(0,52,1));
    header.addView(button("Server",this::changeServer),new LinearLayout.LayoutParams(96,52));
    root.addView(header);
    status=label("Verbindung zum Suchserver",12,Color.LTGRAY);
    root.addView(status,new LinearLayout.LayoutParams(-1,30));
    search=new EditText(this);
    search.setSingleLine(true); search.setTextColor(Color.WHITE);
    search.setHintTextColor(Color.LTGRAY); search.setHint("Onion-Suche");
    search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){doSearch();return true;}return false;});
    LinearLayout searchRow=new LinearLayout(this);
    searchRow.addView(search,new LinearLayout.LayoutParams(0,54,1));
    searchRow.addView(button("Suchen",this::doSearch),new LinearLayout.LayoutParams(110,54));
    root.addView(searchRow);
    LinearLayout tabs=new LinearLayout(this);
    for(String t:new String[]{"web","images","videos"}){
      String caption=t.equals("web")?"Web":t.equals("images")?"Bilder":"Videos";
      tabs.addView(button(caption,()->{type=t;if(search.getText().length()>0)doSearch();}),
          new LinearLayout.LayoutParams(0,48,1));
    }
    root.addView(tabs);
    TextView note=label("HTTP: Suchbegriffe werden unverschlüsselt übertragen.",11,Color.rgb(250,181,117));
    root.addView(note,new LinearLayout.LayoutParams(-1,30));
    page=new WebView(this);
    page.setBackgroundColor(Color.rgb(12,22,35));
    WebSettings settings=page.getSettings();
    settings.setJavaScriptEnabled(false);
    settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
    settings.setDomStorageEnabled(false);settings.setGeolocationEnabled(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
    CookieManager.getInstance().setAcceptCookie(false);
    CookieManager.getInstance().setAcceptThirdPartyCookies(page,false);
    page.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req){
        return navigate(req.getUrl(),req.isForMainFrame());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url){
        return navigate(Uri.parse(url),true);
      }
      @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
        if(internal(req.getUrl()))return null;
        return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",new HashMap<>(),new ByteArrayInputStream(new byte[0]));
      }
      @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
        failed=false;status.setText("Lade: "+Uri.parse(server).getHost());
      }
      @Override public void onPageFinished(WebView v,String url){
        if(!failed)status.setText("Verbunden: "+Uri.parse(server).getHost());
      }
      @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){
        if(req.isForMainFrame()){failed=true;status.setText("Suchserver nicht erreichbar");}
      }
      @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){
        handler.cancel();failed=true;status.setText("HTTPS-Zertifikatsfehler");
      }
    });
    root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
    page.loadUrl(server);
  }
  private static String validServer(String value) {
    if(value==null || value.isBlank())return null;
    try{
      Uri u=Uri.parse(value.trim());
      String scheme=u.getScheme(),host=u.getHost();
      if(scheme==null || (!scheme.equalsIgnoreCase("http")&&!scheme.equalsIgnoreCase("https")))return null;
      if(host==null || !host.matches("(?i)[a-z0-9.-]+") || host.endsWith(".onion") || host.contains(".."))return null;
      if(u.getUserInfo()!=null || u.getFragment()!=null || u.getQuery()!=null)return null;
      String path=u.getEncodedPath();
      if(path==null||path.isEmpty()||path.equals("/"))path="/onionfinder/";
      String p=path.toLowerCase(Locale.ROOT);
      if(p.contains("..")||p.contains("%2f")||p.contains("%2e")||p.contains("%5c")||p.contains("//")||p.contains("\\"))return null;
      if(!path.endsWith("/"))path+="/";
      int port=u.getPort();
      if(port==0||port>65535||port < -1)return null;
      return scheme.toLowerCase(Locale.ROOT)+"://"+host.toLowerCase(Locale.ROOT)+(port<0?"":":"+port)+path;
    }catch(Exception e){return null;}
  }
  private boolean internal(Uri url){
    try{
      Uri u=Uri.parse(server);
      return u.getScheme().equalsIgnoreCase(url.getScheme()) &&
        u.getHost().equalsIgnoreCase(url.getHost()) &&
        u.getPort()==url.getPort() &&
        url.getEncodedPath()!=null && url.getEncodedPath().startsWith(u.getEncodedPath()) &&
        !url.getEncodedPath().toLowerCase(Locale.ROOT).contains("%2f") &&
        !url.getEncodedPath().toLowerCase(Locale.ROOT).contains("%2e");
    }catch(Exception e){return false;}
  }
  private boolean navigate(Uri target,boolean mainFrame){
    if(internal(target))return false;
    if(!mainFrame)return true;
    String scheme=target.getScheme(),host=target.getHost();
    if(host==null||scheme==null || (!scheme.equalsIgnoreCase("https")&&!scheme.equalsIgnoreCase("http"))){
      toast("Link blockiert");return true;
    }
    if(host.toLowerCase(Locale.ROOT).endsWith(".onion")){
      if(!ONION.matcher(host).matches()){toast("Ungültige Onion-Adresse");return true;}
      for(String pkg:new String[]{"org.torproject.torbrowser","org.torproject.torbrowser_alpha"}){
        try{
          Intent i=new Intent(Intent.ACTION_VIEW,target);
          i.setPackage(pkg);i.addCategory(Intent.CATEGORY_BROWSABLE);
          startActivity(i);return true;
        }catch(Exception ignored){}
      }
      new AlertDialog.Builder(this).setTitle("Tor Browser erforderlich")
        .setMessage("Onion-Seiten werden nur im Tor Browser geöffnet.")
        .setPositiveButton("Tor Browser", (d,w)->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.torproject.org/download/#android"))))
        .setNegativeButton("Abbrechen",null).show();
    }else{
      new AlertDialog.Builder(this).setTitle("Externe Webseite")
        .setMessage("Außerhalb von OnionFinder im Browser öffnen?")
        .setNegativeButton("Abbrechen",null)
        .setPositiveButton("Öffnen",(d,w)->{
          try{startActivity(new Intent(Intent.ACTION_VIEW,target));}
          catch(Exception e){toast("Kein Browser gefunden");}
        }).show();
    }
    return true;
  }
  private void doSearch(){
    String q=search.getText().toString().trim();
    if(q.isEmpty()){toast("Suchbegriff eingeben");return;}
    if(q.length()>160)q=q.substring(0,160);
    String url=server+"?q="+URLEncoder.encode(q,StandardCharsets.UTF_8)+"&type="+type;
    page.loadUrl(url);
  }
  private void changeServer(){
    EditText input=new EditText(this);input.setSingleLine(true);input.setText(server);
    new AlertDialog.Builder(this).setTitle("OnionFinder-Suchserver")
      .setMessage("Server-IP oder Domain inklusive http:// oder https://")
      .setView(input)
      .setNegativeButton("Abbrechen",null)
      .setPositiveButton("Speichern",(d,w)->{
        String next=validServer(input.getText().toString());
        if(next==null){toast("Ungültige Server-Adresse");return;}
        server=next;
        getPreferences(MODE_PRIVATE).edit().putString("server",next).apply();
        page.clearHistory();page.loadUrl(server);
      }).show();
  }
  @Override public void onBackPressed(){if(page!=null&&page.canGoBack())page.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){if(page!=null){page.stopLoading();page.destroy();}super.onDestroy();}
}

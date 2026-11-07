package za.co.loopmanager;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import za.co.bacy.ui.BrandFonts;
import za.co.bacy.ui.CustomerHome;

/** Isolated hosted checkout. No JavaScript bridge, card fields, browser allowlisting or payment approval. */
public final class PaymentActivity extends AppCompatActivity {
 private WebView web;private String paymentUrl;
 private static final String[] PAYMENT_HOSTS={"ikhokha.com","ikhokha.red","peachpayments.com","oppwa.com","bankserv.co.za","paygate.co.za","payfast.co.za","ozow.com","entersekt.com","cardinalcommerce.com","arcot.com","3dsecure.co.za","netcetera.com","visa.com","mastercard.com","capitecbank.co.za","fnb.co.za","standardbank.co.za","nedbank.co.za","absa.co.za","africanbank.co.za","discovery.co.za"};
 static boolean initialUrl(String value){try{Uri u=Uri.parse(value);String host=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&host!=null&&(domain(host,"ikhokha.com")||domain(host,"ikhokha.red"));}catch(Exception e){return false;}}
 private static boolean domain(String host,String suffix){return host.equals(suffix)||host.endsWith("."+suffix);}
 static boolean returnUrl(String value){try{Uri u=Uri.parse(value);return "https".equals(u.getScheme())&&"loop-secure-pay.loopsecure-bacanyclinda.workers.dev".equals(u.getHost())&&"/return".equals(u.getPath())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}}
 static boolean navigationUrl(String value){try{Uri u=Uri.parse(value);if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||u.getHost()==null)return false;for(String host:PAYMENT_HOSTS)if(domain(u.getHost(),host))return true;return false;}catch(Exception e){return false;}}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 @SuppressLint("SetJavaScriptEnabled") @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);paymentUrl=getIntent().getStringExtra("payment_url");if(!initialUrl(paymentUrl)){finish();return;}
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CustomerHome.PAPER);root.setOnApplyWindowInsetsListener((v,i)->{var b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(10),dp(16),dp(10));TextView back=label("← Return",13);back.setPadding(dp(8),dp(12),dp(8),dp(12));back.setOnClickListener(v->finish());bar.addView(back);TextView title=label("Secure payment",14);title.setGravity(Gravity.CENTER);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView copy=label("Copy link",12);copy.setPadding(dp(8),dp(12),0,dp(12));copy.setOnClickListener(v->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("iKhokha payment link",paymentUrl));android.widget.Toast.makeText(this,"Payment link copied. You can pay from another device.",android.widget.Toast.LENGTH_LONG).show();});bar.addView(copy);root.addView(bar);
  web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(returnUrl(url)){android.widget.Toast.makeText(PaymentActivity.this,"Checking payment confirmation with iKhokha. Keep your phone online.",android.widget.Toast.LENGTH_LONG).show();finish();return true;}if(!request.isForMainFrame()&&"https".equals(request.getUrl().getScheme()))return false;if(navigationUrl(url))return false;new MaterialAlertDialogBuilder(PaymentActivity.this).setTitle("Continue payment safely").setMessage("This bank step needs a different browser or banking app. Copy this payment link and complete it on another device. Loop Secure will update after iKhokha confirms payment.").setPositiveButton("Close",null).show();return true;}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);web.loadUrl(paymentUrl);
 }
 private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(CustomerHome.INK);t.setTypeface(BrandFonts.sans(this,true));t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}

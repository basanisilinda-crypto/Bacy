package za.co.loopmanager;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import za.co.bacy.ui.BrandFonts;
import za.co.bacy.ui.CustomerHome;

/** Hosted checkout: bank verification stays on the provider; redirects never approve payment. */
public final class PaymentActivity extends AppCompatActivity {
 private WebView web,popup;private FrameLayout pages;private LinearLayout errorPanel;private TextView errorText;private String paymentUrl;private boolean leased;
 private static final String[] PAYMENT_HOSTS={"ikhokha.com","ikhokha.red","peachpayments.com","oppwa.com","bankserv.co.za","paygate.co.za","payfast.co.za","ozow.com","entersekt.com","cardinalcommerce.com","arcot.com","3dsecure.co.za","netcetera.com","visa.com","mastercard.com","capitecbank.co.za","fnb.co.za","standardbank.co.za","nedbank.co.za","absa.co.za","africanbank.co.za","discovery.co.za"};
 private static boolean domain(String host,String suffix){return host.equals(suffix)||host.endsWith("."+suffix);}
 static boolean initialUrl(String value){try{Uri u=Uri.parse(value);return secure(u)&&(domain(u.getHost(),"ikhokha.com")||domain(u.getHost(),"ikhokha.red"));}catch(Exception e){return false;}}
 private static boolean secure(Uri u){return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getHost()!=null&&(u.getPort()==-1||u.getPort()==443);}
 static boolean returnUrl(String value){try{Uri u=Uri.parse(value);return secure(u)&&"loop-secure-pay.loopsecure-bacanyclinda.workers.dev".equals(u.getHost())&&"/return".equals(u.getPath());}catch(Exception e){return false;}}
 static boolean navigationUrl(String value){try{Uri u=Uri.parse(value);if("https".equals(u.getScheme())&&u.getUserInfo()==null&&"dsecure.tymedigital.com".equals(u.getHost())&&(u.getPort()==9643||u.getPort()==443||u.getPort()==-1)&&u.getPath()!=null&&u.getPath().startsWith("/acs/"))return true;if(!secure(u))return false;for(String host:PAYMENT_HOSTS)if(domain(u.getHost(),host))return true;return false;}catch(Exception e){return false;}}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @SuppressLint("SetJavaScriptEnabled") @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);paymentUrl=getIntent().getStringExtra("payment_url");if(!initialUrl(paymentUrl)){finish();return;}CheckoutAccess.begin();leased=true;
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(CustomerHome.PAPER);root.setOnApplyWindowInsetsListener((v,i)->{var b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(4),dp(12),dp(4));button(bar,"← Account",this::finish);TextView title=label("Secure payment",14);title.setGravity(Gravity.CENTER);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));button(bar,"Copy link",this::copy);root.addView(bar);
  LinearLayout verification=new LinearLayout(this);verification.setGravity(Gravity.CENTER);button(verification,"Messages / OTP",this::messages);button(verification,"Banking app",this::bank);root.addView(verification);LinearLayout actions=new LinearLayout(this);button(actions,"Check payment",this::check);button(actions,"Open in browser",this::browser);root.addView(actions);
  pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));web=createWeb();pages.addView(web,new FrameLayout.LayoutParams(-1,-1));
  errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setPadding(dp(24),dp(28),dp(24),dp(24));errorPanel.setBackgroundColor(CustomerHome.PAPER);errorText=label("",16);errorText.setGravity(Gravity.START);errorPanel.addView(errorText);button(errorPanel,"Check whether payment succeeded",this::check);button(errorPanel,"Return to secure checkout",()->{closePopup();errorPanel.setVisibility(View.GONE);web.loadUrl(paymentUrl);});button(errorPanel,"Open in browser",this::browser);button(errorPanel,"Copy link for another device",this::copy);errorPanel.setVisibility(View.GONE);pages.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));setContentView(root);getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(popup!=null){if(popup.canGoBack())popup.goBack();else closePopup();}else if(web!=null&&web.canGoBack())web.goBack();else finish();}});web.loadUrl(paymentUrl);
 }
 @SuppressLint("SetJavaScriptEnabled") private WebView createWeb(){WebView view=new WebView(this);WebSettings s=view.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(true);s.setCacheMode(WebSettings.LOAD_NO_CACHE);CookieManager.getInstance().setAcceptThirdPartyCookies(view,true);
  view.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){String url=request.getUrl().toString();if(request.isForMainFrame()&&returnUrl(url)){check();return true;}if(!request.isForMainFrame()&&"https".equals(request.getUrl().getScheme()))return false;if(navigationUrl(url))return false;failed("This bank step cannot open safely inside checkout. Use your banking app or complete the copied payment link on another device. Your payment has not been marked successful.");return true;}
   @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){if(!"about:blank".equals(url)&&!navigationUrl(url)&&!returnUrl(url)){w.stopLoading();failed("This redirect is not a supported bank checkout. Copy your payment link to continue on another device.");}}
   @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()||"dsecure.tymedigital.com".equals(request.getUrl().getHost()))failed("The bank verification page could not connect. Check that online card payments are enabled in your bank, and try Wi-Fi or mobile data without a failing VPN/proxy. If your bank already debited you, check payment status before trying again.");}
   @Override public void onReceivedHttpError(WebView w,WebResourceRequest request,WebResourceResponse error){if(request.isForMainFrame())failed("The bank or payment provider could not complete this page. Check payment status before retrying.");}
   @Override public void onReceivedSslError(WebView w,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();failed("The bank connection could not be verified securely. No certificate checks were bypassed. Try a different connection or contact your bank.");}
  });
  view.setWebChromeClient(new WebChromeClient(){@Override public boolean onCreateWindow(WebView parent,boolean dialog,boolean gesture,Message message){if(popup!=null)return false;popup=createWeb();pages.addView(popup,new FrameLayout.LayoutParams(-1,-1));WebView.WebViewTransport transport=(WebView.WebViewTransport)message.obj;transport.setWebView(popup);message.sendToTarget();return true;}@Override public void onCloseWindow(WebView window){closePopup();}});return view;
 }
 private void button(LinearLayout parent,String text,Runnable action){TextView v=label(text,12);v.setPadding(dp(10),dp(12),dp(10),dp(12));v.setTextColor(CustomerHome.BLUE);v.setOnClickListener(w->action.run());if(parent.getOrientation()==LinearLayout.HORIZONTAL)parent.addView(v,new LinearLayout.LayoutParams(0,-2,1));else parent.addView(v);}
 private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(CustomerHome.INK);v.setTypeface(BrandFonts.sans(this,true));v.setMinHeight(dp(48));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private org.json.JSONObject policy(){try{Entitlement e=Entitlement.load(this,new Store(this));return e==null?null:e.data.optJSONObject("policy");}catch(Exception unavailable){return null;}}
 private void messages(){try{CheckoutAccess.openMessages(this,policy());android.widget.Toast.makeText(this,"Copy your code, then use Back or Recents to return to payment.",android.widget.Toast.LENGTH_LONG).show();}catch(Exception e){notice(e.getMessage());}}
 private void bank(){var banks=BankApps.installed(this,policy());if(banks.isEmpty()){notice("No supported banking app is installed. You can still enter your SMS code or pay from another device.");return;}String[] names=new String[banks.size()];for(int i=0;i<names.length;i++)names[i]=banks.get(i).label;new MaterialAlertDialogBuilder(this).setTitle("Approve payment in your bank").setItems(names,(d,i)->{try{BankApps.open(this,banks.get(i),policy());}catch(Exception e){notice("The banking app could not open. Try SMS verification or another device.");}}).setNegativeButton("Close",null).show();}
 private void check(){setResult(RESULT_OK);Intent i=new Intent(this,MainActivity.class).putExtra("check_payment",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);finish();}
 private void copy(){getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("iKhokha payment link",paymentUrl));notice("Payment link copied. Use the same link on another device; check payment status before paying again.");}
 private void browser(){if(getSystemService(ActivityManager.class).getLockTaskModeState()!=ActivityManager.LOCK_TASK_MODE_NONE){notice("This phone is restricted. Copy the link and pay on another device, or use the Messages and Banking app buttons here.");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(paymentUrl)).addCategory(Intent.CATEGORY_BROWSABLE));}catch(Exception e){notice("No browser is available. Copy this link to another device.");}}
 private void failed(String text){if(isFinishing()||errorPanel==null)return;errorText.setText(text);errorPanel.setVisibility(View.VISIBLE);errorPanel.bringToFront();}
 private void notice(String text){new MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton("OK",null).show();}
 private void closePopup(){if(popup!=null){pages.removeView(popup);popup.stopLoading();popup.destroy();popup=null;}}
 @Override protected void onStart(){super.onStart();ManagedUpdates.visible(true);}
 @Override protected void onStop(){ManagedUpdates.visible(false);super.onStop();}

 @Override protected void onDestroy(){closePopup();if(web!=null){web.stopLoading();web.destroy();web=null;}if(leased){CheckoutAccess.end();try{Entitlement e=Entitlement.load(this,new Store(this));if(e!=null)PolicyController.apply(getApplicationContext(),e);}catch(Exception ignored){}}super.onDestroy();}
}

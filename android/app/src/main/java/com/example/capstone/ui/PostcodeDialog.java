package com.example.capstone.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class PostcodeDialog {

    public interface Listener {
        void onSelected(String zipCode, String address);
    }

    private static final String HTML = "<!DOCTYPE html><html><head>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>html,body{margin:0;padding:0;height:100%;}#wrap{width:100%;height:100%;}</style>"
            + "</head><body><div id='wrap'></div>"
            + "<script src='https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'></script>"
            + "<script>"
            + "function start(){"
            + " if(!window.daum||!daum.Postcode){Android.onError();return;}"
            + " new daum.Postcode({width:'100%',height:'100%',"
            + "  oncomplete:function(d){"
            + "   var a=d.roadAddress||d.jibunAddress||d.address;"
            + "   if(d.buildingName){a+=' ('+d.buildingName+')';}"
            + "   Android.onComplete(d.zonecode,a);}"
            + " }).embed(document.getElementById('wrap'));}"
            + "window.onload=start;"
            + "</script></body></html>";

    private PostcodeDialog() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void show(Activity activity, Listener listener) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        Handler main = new Handler(Looper.getMainLooper());
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onComplete(String zipCode, String address) {
                main.post(() -> {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    listener.onSelected(zipCode == null ? "" : zipCode, address == null ? "" : address);
                });
            }

            @JavascriptInterface
            public void onError() {
                main.post(() -> {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    AppDialog.alert(activity, "주소 검색",
                            "주소 검색을 불러오지 못했어요. 인터넷 연결을 확인하거나 직접 입력해주세요.", "확인");
                });
            }
        }, "Android");

        webView.loadDataWithBaseURL("https://postcode.map.daum.net", HTML, "text/html", "UTF-8", null);

        dialog.setContentView(webView);
        dialog.setOnDismissListener(d -> webView.destroy());
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }
}

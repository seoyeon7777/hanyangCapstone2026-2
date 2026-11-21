package com.example.capstone.mypage;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 배송지 주소 검색: 카카오(다음) 우편번호 서비스를 웹 화면으로 띄운다 (무료, 키 필요 없음, 인터넷 필요).
 * 주소를 고르면 우편번호와 주소를 돌려주고 닫는다 → AddressEditActivity가 칸을 채운다.
 */
public class AddressSearchActivity extends AppCompatActivity {

    public static final String EXTRA_ZIP_CODE = "address_zip_code";
    public static final String EXTRA_ADDRESS = "address_road";

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_search);
        MyPageUi.setupToolbar(this, "주소 검색");

        webView = findViewById(R.id.webPostcode);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        // 이 화면은 앱에 들어 있는 postcode.html만 연다 (다른 페이지로 이동하지 않음)
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        // file:// 로 열면 카카오 검색 창(iframe)이 고른 주소를 돌려보낼 곳(origin)이 없어 선택이 전달되지 않는다.
        // 같은 html을 https 주소에서 연 것처럼 띄운다 (실제로 그 주소에 접속하지는 않음)
        webView.loadDataWithBaseURL("https://postcode.capstone.local/", readAsset("postcode.html"),
                "text/html", "utf-8", null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "<p style='padding:40px;text-align:center'>주소 검색을 불러오지 못했어요.</p>";
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    /** postcode.html에서 주소를 고르면 부른다 */
    private class Bridge {
        @JavascriptInterface
        public void onSelected(String zipCode, String address) {
            runOnUiThread(() -> {
                Intent data = new Intent();
                data.putExtra(EXTRA_ZIP_CODE, zipCode == null ? "" : zipCode);
                data.putExtra(EXTRA_ADDRESS, address == null ? "" : address);
                setResult(RESULT_OK, data);
                finish();
            });
        }
    }
}

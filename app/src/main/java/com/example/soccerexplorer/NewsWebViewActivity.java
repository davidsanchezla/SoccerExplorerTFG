package com.example.soccerexplorer;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class NewsWebViewActivity extends AppCompatActivity {

    static final String EXTRA_NEWS_URL = "extra_news_url";
    static final String EXTRA_NEWS_TITLE = "extra_news_title";

    private WebView webViewNews;
    private Uri lockedArticleUri;
    private boolean articleLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_webview);

        MaterialToolbar toolbar = findViewById(R.id.toolbarNewsWeb);
        ProgressBar progressBar = findViewById(R.id.progressWebView);
        webViewNews = findViewById(R.id.webViewNews);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        String title = getIntent().getStringExtra(EXTRA_NEWS_TITLE);
        if (title == null || title.trim().isEmpty()) {
            toolbar.setTitle(R.string.news_webview_default_title);
        } else {
            toolbar.setTitle(title);
        }

        String url = getIntent().getStringExtra(EXTRA_NEWS_URL);
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, R.string.news_error_no_url, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        articleLocked = false;

        configurarWebView(progressBar);
        webViewNews.loadUrl(url);
    }

    private void configurarWebView(@NonNull ProgressBar progressBar) {
        WebSettings webSettings = webViewNews.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webViewNews.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }

                if (lockedArticleUri == null) {
                    return false;
                }

                Uri targetUri = request.getUrl();
                if (esMismaNoticia(lockedArticleUri, targetUri)) {
                    return false;
                }

                if (!articleLocked) {
                    return false;
                }

                Toast.makeText(NewsWebViewActivity.this, R.string.news_webview_navigation_blocked, Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!articleLocked && url != null && !url.trim().isEmpty()) {
                    lockedArticleUri = normalizarUri(Uri.parse(url));
                    articleLocked = true;
                    view.clearHistory();
                }
                super.onPageFinished(view, url);
            }
        });

        webViewNews.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                progressBar.setProgress(newProgress);
            }
        });
    }

    private boolean esMismaNoticia(@NonNull Uri origen, @NonNull Uri destino) {
        Uri normalizedTarget = normalizarUri(destino);
        return origen.equals(normalizedTarget);
    }

    @NonNull
    private Uri normalizarUri(@NonNull Uri uri) {
        String normalizedPath = uri.getPath();
        if (normalizedPath != null && normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        Uri.Builder builder = new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(normalizedPath);

        if (uri.getQuery() != null && !uri.getQuery().trim().isEmpty()) {
            builder.encodedQuery(uri.getEncodedQuery());
        }

        builder.fragment(null);
        return builder.build();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (webViewNews != null) {
            webViewNews.stopLoading();
            webViewNews.destroy();
            webViewNews = null;
        }
        super.onDestroy();
    }
}

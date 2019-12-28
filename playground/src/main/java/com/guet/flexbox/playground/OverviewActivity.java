package com.guet.flexbox.playground;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.guet.flexbox.HostingView;
import com.guet.flexbox.Page;
import com.guet.flexbox.TemplateNode;
import com.guet.flexbox.databinding.Toolkit;
import com.guet.flexbox.playground.widget.QuickHandler;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import ch.ielse.view.SwitchView;
import es.dmoral.toasty.Toasty;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OverviewActivity
        extends AppCompatActivity
        implements View.OnClickListener,
        HostingView.EventHandler,
        NestedScrollView.OnScrollChangeListener,
        SwipeRefreshLayout.OnRefreshListener {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private HostingView mLithoView;
    private SwitchView mIsLiveReload;
    private SwitchView mIsOpenConsole;
    private ListView mConsole;
    private Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            if (mIsLiveReload.isOpened()) {
                onRefresh();
            }
            mMainThread.postDelayed(this, 1000);
        }
    };

    private Handler mMainThread = new Handler();
    private QuickHandler mNetwork = new QuickHandler("network");
    private MockService mMockService;
    private ArrayAdapter<String> mAdapter;
    private Page mLayout;
    private Runnable mReload = new Runnable() {
        @WorkerThread
        @Override
        public void run() {
            try {
                Response<Map<String, Object>> dataResponse = mMockService.data().execute();
                Response<TemplateNode> layout = mMockService.layout().execute();
                Map<String, Object> dataBody = dataResponse.body();
                TemplateNode layoutBody = layout.body();
                mLayout = Toolkit.preload(
                        getApplicationContext(),
                        Objects.requireNonNull(layoutBody),
                        dataBody
                );
                runOnUiThread(() -> {
                    apply();
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toasty.error(getApplicationContext(), "刷新失败").show();
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }
    };

    private void apply() {
        if (mLayout != null) {
            mLithoView.setContentAsync(mLayout);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mNetwork.removeCallbacks(mRefresh);
        mNetwork.post(mRefresh);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNetwork.removeCallbacks(mRefresh);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mLithoView = findViewById(R.id.host);
        mSwipeRefreshLayout = findViewById(R.id.pull);
        mIsLiveReload = findViewById(R.id.is_live_reload);
        mIsOpenConsole = findViewById(R.id.is_open_console);
        mConsole = findViewById(R.id.console);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mIsOpenConsole.setOnClickListener(this);
        mAdapter = new ArrayAdapter<>(this, R.layout.console_item, R.id.text);
        mConsole.setAdapter(mAdapter);
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String url = bundle.getString("url");
            if (url != null) {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(url);
                }
                mMockService = new Retrofit.Builder()
                        .baseUrl(url)
                        .client(new OkHttpClient())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(MockService.class);
                onRefresh();
                return;
            }
        }
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.is_open_console) {
            mConsole.setVisibility(mIsOpenConsole.isOpened() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void handleEvent(@NotNull View v, @NotNull String key, @NotNull Object[] value) {
        mAdapter.add("event type=" + key + " : event values=" + Arrays.toString(value));
    }

    @Override
    public void onScrollChange(
            NestedScrollView v,
            int scrollX,
            int scrollY,
            int oldScrollX,
            int oldScrollY
    ) {
        if (!mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setEnabled(scrollY <= 0);
        } else {
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainThread.removeCallbacksAndMessages(null);
        mNetwork.getLooper().quit();
    }

    @Override
    public void onRefresh() {
        mNetwork.removeCallbacks(mReload);
        mNetwork.post(mReload);
    }

}
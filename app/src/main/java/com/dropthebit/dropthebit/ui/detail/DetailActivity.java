package com.dropthebit.dropthebit.ui.detail;

import android.arch.lifecycle.ViewModelProviders;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import com.dropthebit.dropthebit.R;
import com.dropthebit.dropthebit.api.DTBProvider;
import com.dropthebit.dropthebit.base.HasViewType;
import com.dropthebit.dropthebit.common.Constants;
import com.dropthebit.dropthebit.common.MarginDecoration;
import com.dropthebit.dropthebit.dto.DTBCoinDTO;
import com.dropthebit.dropthebit.dto.DTBHistoryDTO;
import com.dropthebit.dropthebit.model.CurrencyData;
import com.dropthebit.dropthebit.model.CurrencyType;
import com.dropthebit.dropthebit.provider.room.PriceHistory;
import com.dropthebit.dropthebit.provider.room.PriceHistoryDao;
import com.dropthebit.dropthebit.provider.room.RoomProvider;
import com.dropthebit.dropthebit.provider.room.WalletDao;
import com.dropthebit.dropthebit.ui.adapter.DetailRecyclerAdapter;
import com.dropthebit.dropthebit.ui.transaction.TransactionDialog;
import com.dropthebit.dropthebit.util.AndroidUtils;
import com.dropthebit.dropthebit.util.RxUtils;
import com.dropthebit.dropthebit.viewmodel.CurrencyViewModel;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DetailActivity extends AppCompatActivity implements TransactionDialog.OnTransactionListener {

    @BindView(R.id.text_coin_name)
    TextView textCoinName;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    // 코인 타입
    private CurrencyType type;
    // 차트 데이터
    private PriceHistory[] historyList;
    // 로컬 데이터 접근 권한
    private PriceHistoryDao priceHistoryDao;
    // 지갑 데이터베이스 접근
    private WalletDao walletDao;
    // Observable 취소 용도
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private DetailRecyclerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // 자세히 보여줄 타입
        type = (CurrencyType) getIntent().getSerializableExtra(Constants.ARGUMENT_TYPE);

        // SQLite 접근 객체
        priceHistoryDao = RoomProvider.getInstance(this)
                .getDatabase()
                .priceHistoryDao();

        walletDao = RoomProvider.getInstance(this).getDatabase().walletDao();

        // 뷰 바인딩
        ButterKnife.bind(this);
        initView();

        // 서버로부터 데이터 최신화
        updateLocalFromRemote();
    }

    @Override
    protected void onStop() {
        super.onStop();
        compositeDisposable.clear();
    }

    @Override
    public void onTransaction(double amount, long price) {
        updateAmount();
    }

    private void initView() {
        String[] coinNames = getResources().getStringArray(R.array.coinNames);
        textCoinName.setText(coinNames[type.ordinal()]);
        adapter = new DetailRecyclerAdapter();
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new MarginDecoration(0, (int) AndroidUtils.dp2px(this, 10F), 0));
        recyclerView.setItemAnimator(null);
        CurrencyViewModel currencyViewModel = ViewModelProviders.of(this).get(CurrencyViewModel.class);
        currencyViewModel.getTotalMapLiveData().observe(this, map -> {
            if (map != null && map.containsKey(type)) {
                adapter.setCoinData(map.get(type));
            }
        });

//        updateAmount();

        RxUtils.clickOne(findViewById(R.id.button_buy), 1000, v -> {
            TransactionDialog dialog = TransactionDialog.newInstance(TransactionDialog.TYPE_BUY, type);
            dialog.show(getSupportFragmentManager(), Constants.TAG_TRANSACTION);
        });

        RxUtils.clickOne(findViewById(R.id.button_sell), 1000, v -> {
            TransactionDialog dialog = TransactionDialog.newInstance(TransactionDialog.TYPE_SELL, type);
            dialog.show(getSupportFragmentManager(), Constants.TAG_TRANSACTION);
        });
    }

    private void updateAmount() {
//        walletDao.loadWallet(type.key)
//                .subscribeOn(Schedulers.io())
//                .map(wallet -> wallet.amount)
//                .subscribe(a -> amount = a);
    }

    /**
     * 서버로부터 데이터를 최신화 하는 함수
     */
    private void updateLocalFromRemote() {
        showChartLoadingIndicator(true);
        Disposable disposable =
                // 가장 최근 데이터 로드
                priceHistoryDao.loadRecentHistory(type.key)
                        .subscribeOn(Schedulers.io())
                        .subscribe(history -> requestHistories(history.time + 1),
                                Throwable::printStackTrace,
                                () -> requestHistories(0));
        compositeDisposable.add(disposable);
    }

    private void requestHistories(long time) {
        Disposable disposable = DTBProvider.getInstance().getHistory(type, time)
                // data만 필요함
                .map(DTBCoinDTO::getData)
                // 시작 스케줄러 io
                .subscribeOn(Schedulers.io())
                .subscribe(list ->
                {
                    // 불러온 목록으로 데이터베이스 업데이트
                    updateLocalDatabase(list);
                    // 차트 업데이트
                    updateChart();

                }, throwable -> {
                    throwable.printStackTrace();
                    finish();
                });
        compositeDisposable.add(disposable);
    }

    /**
     * 데이터베이스 목록 업데이트
     *
     * @param list 업데이트할 데이터
     */
    private void updateLocalDatabase(List<DTBHistoryDTO> list) {
        for (DTBHistoryDTO dto : list) {
            priceHistoryDao.insertPriceHistories(new PriceHistory(dto.getName(), dto.getTime(), dto.getPrice()));
        }
    }

    /**
     * 차트에 데이터를 보여주는 함수
     */
    private void updateChart() {
        // 로컬에 있는 데이터 기반으로 차트 업데이트 요청
//        Disposable disposable = priceHistoryDao.loadAllHistories(type.key)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(list -> {
//                    if (list.length > 0) {
//                        historyList = list;
//                        final int minute = 1000 * 60;   // 1분
//                        List<Entry> entries = new ArrayList<>();
//                        for (PriceHistory history : historyList) {
//                            // 10분 단위로 x 좌표 찍기, y 좌표는 price
//                            entries.add(new Entry(history.time / (10 * minute), history.price));
//                        }
//                        // 데이터 설정
//                        LineDataSet dataSet = new LineDataSet(entries, type.key);
//                        LineData lineData = new LineData(dataSet);
//                        // 데이터 표시 제거
//                        lineData.setValueFormatter((value, entry, dataSetIndex, viewPortHandler) -> "");
//                        lineChart.setData(lineData);
//                        // 보이는 데이터 최대 100개
//                        lineChart.setVisibleXRangeMaximum(100);
//                        // 차트 맨 끝 으로 이동
//                        lineChart.moveViewToX(entries.get(entries.size() - 1).getX());
//                        // 그리기
//                        lineChart.invalidate();
//                        showChartLoadingIndicator(false);
//                    }
//                }, throwable -> {
//                    throwable.printStackTrace();
//                    showChartLoadingIndicator(false);
//                });
//        compositeDisposable.add(disposable);
    }

    private void showChartLoadingIndicator(boolean show) {
//        progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
//        lineChart.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    }
}

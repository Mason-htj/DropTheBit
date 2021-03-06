package com.dropthebit.dropthebit.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import com.dropthebit.dropthebit.R;
import com.dropthebit.dropthebit.api.BithumbProvider;
import com.dropthebit.dropthebit.common.Constants;
import com.dropthebit.dropthebit.dto.BithumbAllDTO;
import com.dropthebit.dropthebit.dto.BithumbCurrencyDTO;
import com.dropthebit.dropthebit.model.CurrencyData;
import com.dropthebit.dropthebit.model.CurrencyType;
import com.dropthebit.dropthebit.provider.room.InterestCoin;
import com.dropthebit.dropthebit.provider.room.InterestCoinDao;
import com.dropthebit.dropthebit.provider.room.RoomProvider;
import com.dropthebit.dropthebit.util.CurrencyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by mason-hong on 2017. 12. 17..
 */
public class CurrencyViewModel extends AndroidViewModel {
    private MutableLiveData<LinkedHashMap<CurrencyType, CurrencyData>> totalMapLiveData = new MutableLiveData<>();
    private LinkedHashMap<CurrencyType, CurrencyData> cachedTotalMap;
    private String[] coinNames;

    private MutableLiveData<LinkedHashMap<CurrencyType, CurrencyData>> interestMapLiveData = new MutableLiveData<>();
    private LinkedHashMap<CurrencyType, CurrencyData> cachedInterestMap;
    private List<InterestCoin> interestList;
    private InterestCoinDao interestCoinDao;

    private Disposable disposableTotal = null;
    private Disposable disposableInterest = null;

    public CurrencyViewModel(@NonNull Application application) {
        super(application);
        // 프래그먼트가 종료되었을 때 메모리 해제를 위해 저장하며
        // interval을 사용해서 주기적으로 호출 할 수 있도록 한다

        coinNames = application.getResources().getStringArray(R.array.coinNames);
        disposableTotal = Observable.interval(0, Constants.PERIOD_UPDATE, TimeUnit.MILLISECONDS)
                .flatMap(aLong -> {
                    // 호출할 데이터는 Bithumb API
                    return BithumbProvider.getInstance().getAllPrices();
                })
                .map(BithumbAllDTO::getData)
                // io Scheduler에서 관리
                .subscribeOn(Schedulers.io())
                // 결과는 mainThread에서
                .subscribe(data -> {
                    LinkedHashMap<CurrencyType, CurrencyData> map = new LinkedHashMap<>();
                    BithumbCurrencyDTO target = data.getBTC();
                    map.put(CurrencyType.BitCoin, new CurrencyData(CurrencyType.BitCoin, coinNames[0], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getBCH();
                    map.put(CurrencyType.BitCoinCache, new CurrencyData(CurrencyType.BitCoinCache, coinNames[1], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getBTG();
                    map.put(CurrencyType.BitCoinGold, new CurrencyData(CurrencyType.BitCoinGold, coinNames[2], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getETH();
                    map.put(CurrencyType.Etherium, new CurrencyData(CurrencyType.Etherium, coinNames[3], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getETC();
                    map.put(CurrencyType.EtheriumClassic, new CurrencyData(CurrencyType.EtheriumClassic, coinNames[4], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getXRP();
                    map.put(CurrencyType.Ripple, new CurrencyData(CurrencyType.Ripple, coinNames[5], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getLTC();
                    map.put(CurrencyType.LiteCoin, new CurrencyData(CurrencyType.LiteCoin, coinNames[6], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getQTUM();
                    map.put(CurrencyType.Qtum, new CurrencyData(CurrencyType.Qtum, coinNames[7], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    target = data.getDASH();
                    map.put(CurrencyType.Dash, new CurrencyData(CurrencyType.Dash, coinNames[8], target.getClosing_price(), target.getMax_price(), target.getMin_price(), target.getOpening_price()));
                    cachedTotalMap = map;
                    totalMapLiveData.postValue(cachedTotalMap);
                    updateInterestCoins();
                });

        interestCoinDao = RoomProvider.getInstance(application).getDatabase().interestCoinDao();
        disposableInterest = interestCoinDao.loadAllInterestCoins()
                .subscribeOn(Schedulers.io())
                .subscribe(list -> {
                    interestList = new ArrayList<>(Arrays.asList(list));
                    if (cachedTotalMap != null) {
                        updateInterestCoins();
                    }
                });
    }

    public MutableLiveData<LinkedHashMap<CurrencyType, CurrencyData>> getTotalMapLiveData() {
        return totalMapLiveData;
    }

    public MutableLiveData<LinkedHashMap<CurrencyType, CurrencyData>> getInterestList() {
        return interestMapLiveData;
    }

    public void addInterestCoin(String name) {
        InterestCoin newCoin = new InterestCoin(name);
        if (!interestList.contains(newCoin)) {
            interestList.add(new InterestCoin(name));
            updateInterestCoins();
        }
    }

    public void removeInterestCoin(String name) {
        Iterator<InterestCoin> iterator = interestList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().name.equals(name)) {
                iterator.remove();
                break;
            }
        }
        updateInterestCoins();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (disposableTotal != null) {
            disposableTotal.dispose();
        }
        if (disposableInterest != null) {
            disposableInterest.dispose();
        }
    }

    private void updateInterestCoins() {
        if (cachedInterestMap == null) {
            cachedInterestMap = new LinkedHashMap<>();
        }
        for (InterestCoin coin : interestList) {
            CurrencyType type = CurrencyUtils.findByName(coin.name);
            if (cachedTotalMap.containsKey(type)) {
                cachedInterestMap.put(type, cachedTotalMap.get(type));
            }
        }
        interestMapLiveData.postValue(cachedInterestMap);
    }
}

package com.dropthebit.dropthebit.provider.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import io.reactivex.Flowable;
import io.reactivex.Maybe;

/**
 * Created by mason-hong on 2017. 12. 18..
 */
@Dao
public interface PriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPriceHistories(PriceHistory... priceHistories);

    @Query("SELECT * FROM priceHistories WHERE name = :name")
    Flowable<PriceHistory[]> loadAllHistories(String name);

    @Query("select * from priceHistories where name = :name order by time desc limit 1")
    Maybe<PriceHistory> loadRecentHistory(String name);
}

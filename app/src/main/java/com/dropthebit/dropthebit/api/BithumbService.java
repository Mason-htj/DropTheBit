package com.dropthebit.dropthebit.api;

import com.dropthebit.dropthebit.dto.BithumbAllDTO;
import com.dropthebit.dropthebit.dto.BithumbOneDTO;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Created by mason-hong on 2017. 12. 14..
 * Bithumb API
 */
public interface BithumbService {
    @GET("public/ticker/ALL")
    Observable<BithumbAllDTO> getAllPrices();

    @GET("public/ticker/{currency}")
    Observable<BithumbOneDTO> getPrice(@Path("currency") String currency);
}

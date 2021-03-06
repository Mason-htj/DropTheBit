package com.dropthebit.dropthebit.ui.transaction;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dropthebit.dropthebit.R;
import com.dropthebit.dropthebit.common.Constants;
import com.dropthebit.dropthebit.model.CurrencyType;
import com.dropthebit.dropthebit.provider.pref.CommonPref;
import com.dropthebit.dropthebit.provider.room.RoomProvider;
import com.dropthebit.dropthebit.provider.room.Wallet;
import com.dropthebit.dropthebit.provider.room.WalletDao;
import com.dropthebit.dropthebit.util.CurrencyUtils;
import com.dropthebit.dropthebit.viewmodel.CurrencyViewModel;
import com.squareup.picasso.Picasso;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by mason-hong on 2017. 12. 16..
 */
public class TransactionDialog extends DialogFragment {
    public static final int TYPE_BUY = 0;
    public static final int TYPE_SELL = 1;

    // 원하는 값만 사용하도록 경고해주는 용도
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_BUY, TYPE_SELL})
    public @interface TransactionType {
    }

    public interface OnTransactionListener {
        void onTransaction(double amount, long price);
    }

    @BindView(R.id.container_dialog)
    View containerDialog;

    @BindView(R.id.text_title)
    TextView textTitle;

    @BindView(R.id.image_currency)
    ImageView imageCurrency;

    @BindView(R.id.text_name)
    TextView textName;

    @BindView(R.id.text_symbol)
    TextView textSymbol;

    @BindView(R.id.text_price)
    TextView textPrice;

    @BindView(R.id.text_amount)
    TextView textAmount;

    @BindView(R.id.text_time)
    TextView textTime;

    @BindView(R.id.edit_amount)
    EditText editAmount;

    @BindView(R.id.edit_predict_price)
    EditText editPredictPrice;

    @BindView(R.id.progress_time)
    ProgressBar progressTime;

    @BindView(R.id.text_hold)
    TextView textHold;

    @BindView(R.id.text_krw)
    TextView textKRW;

    @TransactionType
    private int transactionType;

    private CurrencyType currencyType;
    private CurrencyViewModel currencyViewModel;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private long realTimePrice = 0;
    private long currentPrice = 0;
    private int currentCount = 0;
    private boolean isFirstLoaded = true;
    private boolean isCommaEditing = false;
    private int cursorPosition = 0;
    private WalletDao walletDao;
    private OnTransactionListener onTransactionListener;
    private boolean isOpened = false;

    public static TransactionDialog newInstance(@TransactionType int transactionType, CurrencyType currencyType) {
        TransactionDialog dialog = new TransactionDialog();
        Bundle args = new Bundle();
        args.putInt(Constants.ARGUMENT_TYPE, transactionType);
        args.putSerializable(Constants.ARGUMENT_CURRENCY_TYPE, currencyType);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTransactionListener) {
            onTransactionListener = (OnTransactionListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, 0);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transaction, container);
        ButterKnife.bind(this, view);
        initView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog().getWindow() != null) {
            WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            getDialog().getWindow().setAttributes(params);
        }
        getDialog().setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    closeDialog();
                    return true;
                }
                return false;
            }
        });
        Disposable disposable = Flowable.interval(0, Constants.PERIOD_TRANSACTION_INTERVAL, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(time -> {
                    currentCount += Constants.PERIOD_TRANSACTION_INTERVAL;
                    float rate = currentCount / (float) Constants.PERIOD_TRANSACTION_REFRESH;
                    progressTime.setProgress((int) (rate * 100));
                    if (currentCount == Constants.PERIOD_TRANSACTION_REFRESH) {
                        currentCount = 0;
                        currentPrice = realTimePrice;
                        NumberFormat numberFormat = NumberFormat.getNumberInstance();
                        textPrice.setText(numberFormat.format(currentPrice));
                    }
                    textTime.setText(String.format(Locale.getDefault(), "%.01f초", (Constants.PERIOD_TRANSACTION_REFRESH - currentCount) / 1000F));
                });
        compositeDisposable.add(disposable);
        if (!isOpened) {
            openDialog();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        compositeDisposable.clear();
    }

    @OnTextChanged(R.id.edit_amount)
    public void onAmountChanged(CharSequence text, int start, int end, int length) {
        if (editAmount.hasFocus()) {
            double amount = 0;
            if (!text.toString().isEmpty()) {
                amount = Double.parseDouble(text.toString());
            }
            long predictPrice = (long) (amount * currentPrice);
            editPredictPrice.setText(NumberFormat.getInstance().format(predictPrice));
        }
    }

    @OnTextChanged(R.id.edit_predict_price)
    public void onPredictPriceChanged(CharSequence text, int start, int end, int length) {
        if (editPredictPrice.hasFocus()) {
            if (isCommaEditing) {
                isCommaEditing = false;
                if (cursorPosition >= text.length()) {
                    cursorPosition = text.length();
                }
                editPredictPrice.setSelection(cursorPosition);
                return;
            }
            isCommaEditing = true;
            cursorPosition = editPredictPrice.getSelectionStart();
            String str = text.toString().replaceAll(",", "");
            long predictPrice = 0;
            if (!text.toString().isEmpty()) {
                predictPrice = Long.parseLong(str);
            }
            double amount = (double) predictPrice / currentPrice;
            editAmount.setText(String.format(Locale.getDefault(), "%f", amount));
            editPredictPrice.setText(NumberFormat.getInstance().format(predictPrice));
        }
    }

    @OnClick(R.id.button_close)
    public void onClickCancel() {
        closeDialog();
    }

    @OnClick(R.id.button_confirm)
    public void onClickConfirm() {
        double amount = Double.parseDouble(editAmount.getText().toString());
        long predictPrice = Long.parseLong(editPredictPrice.getText().toString().replaceAll(",", ""));
        if (amount > 0 && predictPrice > 0) {
            switch (transactionType) {
                case TYPE_BUY:
                    handleBuying();
                    break;
                case TYPE_SELL:
                    handleSelling();
                    break;
                default:
                    closeDialog();
            }
        } else {
            Toast.makeText(getContext(), R.string.transaction_wrong_message, Toast.LENGTH_SHORT).show();
        }
    }

    @OnClick({R.id.text_max_amount, R.id.text_max_predict})
    void onClickMaxAmount() {
        if (transactionType == TYPE_BUY) {
            long krw = CommonPref.getInstance(getContext()).getKRW();
            double maxAmount = krw / (double) currentPrice;
            editAmount.setText(String.format(Locale.getDefault(), "%.06f", maxAmount));
            editPredictPrice.setText(String.format(Locale.getDefault(), "%d", krw));
        } else {
            walletDao.loadWallet(currencyType.key)
                    .subscribeOn(Schedulers.io())
                    .map(wallet -> wallet.amount)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(amount -> {
                        long krw = (long) (amount * currentPrice);
                        editAmount.setText(String.format(Locale.getDefault(), "%.06f", amount));
                        editPredictPrice.setText(String.format(Locale.getDefault(), "%d", krw));
                    });
        }
    }

    @OnClick(R.id.view_dim)
    void onClickDim() {
        closeDialog();
    }

    private void handleBuying() {
        final double amount = Double.parseDouble(editAmount.getText().toString());
        long price = (long) (amount * currentPrice);
        if (CommonPref.getInstance(getContext()).getKRW() >= price) {
            CommonPref.getInstance(getContext()).addKRW(-price);
            walletDao.loadWallet(currencyType.key)
                    .subscribeOn(Schedulers.io())
                    .subscribe(wallet -> {
                                wallet.amount += amount;
                                walletDao.insertWallet(wallet);
                            }, Throwable::printStackTrace,
                            () -> walletDao.insertWallet(new Wallet(currencyType.key, amount)));
            Toast.makeText(getContext(), R.string.transaction_successful_message, Toast.LENGTH_SHORT).show();
            if (onTransactionListener != null) {
                onTransactionListener.onTransaction(amount, price);
            }
            closeDialog();
        } else {
            Toast.makeText(getContext(), R.string.transaction_lack_krw_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelling() {
        double amount = Double.parseDouble(editAmount.getText().toString());
        long price = (long) (amount * currentPrice);
        walletDao.loadWallet(currencyType.key)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(wallet -> {
                            if (wallet.amount >= amount) {
                                CommonPref.getInstance(getContext()).addKRW(price);
                                wallet.amount -= amount;
                                Single.just(wallet)
                                        .subscribeOn(Schedulers.io())
                                        .subscribe(walletDao::insertWallet);
                                Toast.makeText(getContext(), R.string.transaction_successful_message, Toast.LENGTH_SHORT).show();
                                if (onTransactionListener != null) {
                                    onTransactionListener.onTransaction(amount, price);
                                }
                                closeDialog();
                            } else {
                                Toast.makeText(getContext(), R.string.trasaction_lack_currency_message, Toast.LENGTH_SHORT).show();
                            }
                        }, Throwable::printStackTrace,
                        () -> Toast.makeText(getContext(), R.string.trasaction_lack_currency_message, Toast.LENGTH_SHORT).show());
    }

    private void initView() {
        transactionType = getArguments().getInt(Constants.ARGUMENT_TYPE, 0);
        currencyType = (CurrencyType) getArguments().getSerializable(Constants.ARGUMENT_CURRENCY_TYPE);
        Picasso.with(getContext())
                .load(CurrencyUtils.getCoinImage(currencyType))
                .fit()
                .into(imageCurrency);
        String[] names = getResources().getStringArray(R.array.coinNames);
        textName.setText(names[currencyType.ordinal()]);
        textSymbol.setText(currencyType.key);
        currencyViewModel = ViewModelProviders.of(getActivity()).get(CurrencyViewModel.class);
        currencyViewModel.getTotalMapLiveData()
                .observe(this, map -> {
                    if (map.containsKey(currencyType)) {
                        realTimePrice = CurrencyUtils.getSafetyPrice(map.get(currencyType));
                        if (isFirstLoaded) {
                            NumberFormat numberFormat = NumberFormat.getNumberInstance();
                            currentPrice = realTimePrice;
                            textPrice.setText(numberFormat.format(realTimePrice));
                            isFirstLoaded = false;
                        }
                    }
                });
        switch (transactionType) {
            case TYPE_BUY:
                textTitle.setText(R.string.buying);
                textAmount.setText(R.string.amount_of_buying);
                break;
            case TYPE_SELL:
                textTitle.setText(R.string.selling);
                textAmount.setText(R.string.amount_of_selling);
                break;
        }
        walletDao = RoomProvider.getInstance(getContext()).getDatabase().walletDao();
        walletDao.loadWallet(currencyType.key)
                .subscribeOn(Schedulers.io())
                .map(wallet -> wallet.amount)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        amount -> textHold.setText(getString(R.string.hold_amount_text, amount, currencyType.key)),
                        Throwable::printStackTrace,
                        () -> textHold.setText(getString(R.string.hold_amount_text, 0F, currencyType.key)));
        textKRW.setText(getString(R.string.hold_krw_text, CommonPref.getInstance(getContext()).getKRW()));
    }

    private void openDialog() {
        isOpened = true;
        containerDialog.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                containerDialog.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                TranslateAnimation animation = new TranslateAnimation(0F, 0F, containerDialog.getHeight(), 0F);
                animation.setDuration(250);
                animation.setInterpolator(new DecelerateInterpolator());
                containerDialog.startAnimation(animation);
            }
        });
    }

    private void closeDialog() {
        if (!isOpened) {
            return;
        }
        isOpened = false;
        TranslateAnimation animation = new TranslateAnimation(0F, 0F, 0F, containerDialog.getHeight());
        animation.setDuration(250);
        animation.setInterpolator(new AccelerateInterpolator());
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                dismiss();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        containerDialog.startAnimation(animation);
    }
}
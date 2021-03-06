package com.guarda.ethereum.views.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.gravilink.zcash.WalletCallback;
import com.gravilink.zcash.ZCashException;
import com.gravilink.zcash.ZCashTransaction_taddr;
import com.gravilink.zcash.ZCashWalletManager;
import com.gravilink.zcash.crypto.Utils;
import com.guarda.ethereum.GuardaApp;
import com.guarda.ethereum.R;
import com.guarda.ethereum.managers.BitcoinNodeManager;
import com.guarda.ethereum.managers.EthereumNetworkManager;
import com.guarda.ethereum.managers.TransactionsManager;
import com.guarda.ethereum.managers.WalletManager;
import com.guarda.ethereum.models.constants.Common;
import com.guarda.ethereum.models.constants.Extras;
import com.guarda.ethereum.models.items.SendRawTxResponse;
import com.guarda.ethereum.rest.ApiMethods;
import com.guarda.ethereum.utils.CurrencyUtils;
import com.guarda.ethereum.utils.DigitsInputFilter;
import com.guarda.ethereum.views.activity.base.AToolbarMenuActivity;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.WrongNetworkException;

import java.math.BigDecimal;
import java.util.IllegalFormatConversionException;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;

import static com.guarda.ethereum.models.constants.Common.KMD_MIN_CONFIRM;

@AutoInjector(GuardaApp.class)
public class SendingCurrencyActivity extends AToolbarMenuActivity {

    @BindView(R.id.et_sum_send)
    EditText etSumSend;
    @BindView(R.id.et_fee_amount)
    EditText etFeeAmount;
    @BindView(R.id.et_arrival_amount)
    EditText etArrivalAmount;
    @BindView(R.id.et_send_coins_address)
    EditText etWalletAddress;
    @BindView(R.id.btn_include)
    Button btnInclude;
    @BindView(R.id.btn_exclude)
    Button btnExclude;
    @BindView(R.id.btn_confirm)
    Button btnConfirm;
    //    @BindView(R.id.ll_gas_container)
//    LinearLayout gasContainer;
    @BindView(R.id.ll_fee_container)
    LinearLayout feeContainer;
//    @BindView(R.id.switch_gas_fee)
//    SwitchCompat swGasFee;
//    @BindView(R.id.et_gas_limit)
//    EditText etGasLimit;
//    @BindView(R.id.et_custom_data)
//    EditText etCustomData;
//    @BindView(R.id.cl_switch_container)
//    ConstraintLayout switchContainer;
//    @BindView(R.id.tv_gas)
//    TextView tvGas;
//    @BindView(R.id.tv_fee)
//    TextView tvFee;

//    private BigDecimal balance = new BigDecimal(0);

    @Inject
    WalletManager walletManager;

    @Inject
    EthereumNetworkManager networkManager;

    @Inject
    TransactionsManager transactionsManager;

    private String walletNumber;
    private String amountToSend;
    private boolean isInclude = false;
    private long currentFeeEth;
    private String arrivalAmountToSend;


    @Override
    protected void init(Bundle savedInstanceState) {
        GuardaApp.getAppComponent().inject(this);
        setToolBarTitle(getString(R.string.title_withdraw3));
        etSumSend.setFilters(new InputFilter[]{new DigitsInputFilter(8, 8, Float.POSITIVE_INFINITY)});
        etFeeAmount.setFilters(new InputFilter[]{new DigitsInputFilter(8, 8, Float.POSITIVE_INFINITY)});
        walletNumber = getIntent().getStringExtra(Extras.WALLET_NUMBER);
        amountToSend = getIntent().getStringExtra(Extras.AMOUNT_TO_SEND);
        checkBtnIncludeStatus(isInclude);
        setDataToView();
        initSendSumField();
        initFeeField();
        updateArrivalField();
        updateWarnings();
        updateArrivalField();
    }

    private void initSendSumField() {
        etSumSend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError(etSumSend);
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateArrivalField();
                updateWarnings();
                updateArrivalField();
            }
        });
    }

    private void initFeeField() {
        Coin defaultFee = Coin.valueOf(164000);
        currentFeeEth = defaultFee.getValue();
        etFeeAmount.setText(defaultFee.toPlainString());
        updateArrivalField();
        etFeeAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError(etFeeAmount);
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateArrivalField();
                updateWarnings();
                updateArrivalField();
            }
        });
    }

    private void updateWarnings() {
        try {
            String newAmount = etSumSend.getText().toString();
            if (newAmount.length() > 0) {
                findViewById(R.id.eth_hint_sum).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.eth_hint_sum).setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(newAmount)) {
                if (!isAmountMoreBalance(newAmount)) {
                    hideError(etSumSend);
                    amountToSend = newAmount;
                    updateArrivalField();
                } else {
                    showError(etSumSend, getString(R.string.withdraw_amount_more_than_balance));
                }
            } else {
                showError(etSumSend, getString(R.string.withdraw_amount_can_not_be_empty));
            }

            String newFee = etFeeAmount.getText().toString();
            if (newFee.length() > 0) {
                findViewById(R.id.eth_hint_fee).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.eth_hint_fee).setVisibility(View.GONE);
            }
            if (!TextUtils.isEmpty(newFee)) {
                try {
                    currentFeeEth = Coin.parseCoin(etFeeAmount.getText().toString()).getValue();
                    if (currentFeeEth > 0) {
                        btnConfirm.setEnabled(true);
                    } else {
                        btnConfirm.setEnabled(false);
                        showError(etFeeAmount, getString(R.string.et_error_fee_is_empty));
                    }
                } catch (IllegalFormatConversionException e) {
                    btnConfirm.setEnabled(false);
                }
                if (feeLessThanAmountToSend(newFee)
                        && currentFeeEth > 0) {
                    hideError(etFeeAmount);
                    btnConfirm.setEnabled(true);
                } else if (isInclude) {
                    btnConfirm.setEnabled(false);
                    showError(etFeeAmount, getString(R.string.et_error_fee_more_than_amount));
                }
            } else {
                btnConfirm.setEnabled(false);
                showError(etFeeAmount, getString(R.string.et_error_fee_is_empty));
            }

            long amountSatoshi = Coin.parseCoin(getAmountToSend()).getValue();
            if (amountSatoshi < 0) {
                btnConfirm.setEnabled(false);
                showError(etFeeAmount, getString(R.string.et_error_fee_more_than_amount));
            }
        } catch (Exception e) {
            btnConfirm.setEnabled(false);
            showError(etFeeAmount, getString(R.string.withdraw_amount_can_not_be_empty));
        }
    }

    private void checkFeeLessAmount() {
        if (!feeLessThanAmountToSend(etFeeAmount.getText().toString()) && isInclude) {
            showError(etFeeAmount, getString(R.string.et_error_fee_more_than_amount));
            btnConfirm.setEnabled(false);
        } else {
            hideError(etFeeAmount);
            btnConfirm.setEnabled(true);
        }
    }

    private boolean feeLessThanAmountToSend(String newFee) {
//        BigDecimal feeDecimal = new BigDecimal(newFee);
//        BigDecimal amountDecimal = new BigDecimal(etSumSend.getText().toString());
//        return amountDecimal.compareTo(feeDecimal) > 0;
        return !false;
    }

    private void updateArrivalField() {
        Log.d("flint", "SendingCurrencyActivity.updateArrivalField()...");
        boolean makeSecondCheck = true;
        try {
            currentFeeEth = Coin.parseCoin(etFeeAmount.getText().toString()).getValue();
            long sumSatoshi = Coin.parseCoin(etSumSend.getText().toString()).getValue();
            // divide on 2 - to prevent not_enough_money_error in case of sending all wallet's money include fee
            long totalFee = walletManager.calculateFee(getToAddress(), sumSatoshi / 2, currentFeeEth);
            Log.d("flint", "1 amountToSend=" + sumSatoshi + ", currentFeeEth=" + currentFeeEth + ", totalFee=" + totalFee);
            if (isInclude)
                arrivalAmountToSend = Coin.valueOf(sumSatoshi - totalFee).toPlainString();
            else
                arrivalAmountToSend = Coin.valueOf(sumSatoshi + totalFee).toPlainString();
            etArrivalAmount.setText(arrivalAmountToSend);
        } catch (WrongNetworkException wne){
            Log.e("psd", wne.toString());
            String toastStr = String.format(getString(R.string.send_wrong_address), getString(R.string.app_coin_currency));
            Toast.makeText(this, toastStr, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.d("flint", "1 amountToSend=... exception: " + e.toString());
            btnConfirm.setEnabled(false);
            if (e.getMessage() == "SMALL_SENDING") {
                makeSecondCheck = false;
                showError(etFeeAmount, getString(R.string.small_sum_of_tx));
            } else {
                showError(etFeeAmount, getString(R.string.not_enough_money_to_send));
            }
        }
        // arrivalAmountToSend has changed, recalculate fee for preciseness (totalFee should not change)
        if (makeSecondCheck) {
            try {
                long sumSatoshi = Coin.parseCoin(etSumSend.getText().toString()).getValue();
                long amountSatoshi = Coin.parseCoin(getAmountToSend()).getValue();
                long totalFee = walletManager.calculateFee(getToAddress(), amountSatoshi, currentFeeEth);
                Log.d("flint", "2 amountToSend=" + amountSatoshi + ", currentFeeEth=" + currentFeeEth + ", totalFee=" + totalFee);
                if (isInclude)
                    arrivalAmountToSend = Coin.valueOf(sumSatoshi - totalFee).toPlainString();
                else
                    arrivalAmountToSend = Coin.valueOf(sumSatoshi + totalFee).toPlainString();
                etArrivalAmount.setText(arrivalAmountToSend);
            } catch (Exception e) {
                Log.d("flint", "2 amountToSend=... exception: " + e.toString());
                btnConfirm.setEnabled(false);
                if (e.getMessage() == "SMALL_SENDING") {
                    showError(etFeeAmount, getString(R.string.small_sum_of_tx));
                } else {
                    showError(etFeeAmount, getString(R.string.not_enough_money_to_send));
                }
            }
        }
    }

    private void setDataToView() {
        etSumSend.setText(amountToSend);
        etWalletAddress.setText(walletNumber);
    }

    @Override
    protected int getLayout() {
        return R.layout.activity_sending_currency;
    }

    @OnClick({R.id.btn_include, R.id.btn_exclude})
    public void sendingCurrencyButtonClick(View view) {
        switch (view.getId()) {
            case R.id.btn_include:
                isInclude = true;
                checkFeeLessAmount();
                checkBtnIncludeStatus(isInclude);
                updateArrivalField();
                break;
            case R.id.btn_exclude:
                isInclude = false;
                checkFeeLessAmount();
                updateArrivalField();
                checkBtnIncludeStatus(isInclude);
                break;
        }
    }

    private void checkBtnIncludeStatus(boolean isInclude) {
        if (isInclude){
            btnExclude.setBackground(getResources().getDrawable(R.drawable.btn_enable_gray));
            btnInclude.setBackground(getResources().getDrawable(R.drawable.btn_border_blue));
        } else {
            btnInclude.setBackground(getResources().getDrawable(R.drawable.btn_enable_gray));
            btnExclude.setBackground(getResources().getDrawable(R.drawable.btn_border_blue));
        }
    }

    private String getToAddress() {
        return etWalletAddress.getText().toString();
    }

    private String getAmountToSend() {
        if (isInclude)
            return etArrivalAmount.getText().toString();
        else
            return etSumSend.getText().toString();
    }

    @OnClick(R.id.btn_confirm)
    public void onConfirmClick(View view) {
        try {
            String amount = etSumSend.getText().toString();
            if (!amount.isEmpty()) {
                    showProgress();
                    long amountSatoshi = Coin.parseCoin(getAmountToSend()).getValue();
                    Log.d("svcom", "amount=" + amountSatoshi + " fee=" + currentFeeEth);
                    // Here is call from Zcash library for supporting Sapling update, because Komodo is Zcash's fork
                    ZCashWalletManager.getInstance().createTransaction_taddr(walletManager.getWalletFriendlyAddress(),
                            getToAddress(),
                            amountSatoshi,
                            currentFeeEth,
                            walletManager.anyToWif(walletManager.getPrivateKey()),
                            KMD_MIN_CONFIRM, (r1, r2) -> {
                                Log.i("RESPONSE CODE", r1);
                                if (r1.equals("ok")) {
                                    try {
                                        String lastTxhex = Utils.bytesToHex(r2.getBytes());
                                        Log.i("lastTxhex", lastTxhex);
                                        BitcoinNodeManager.sendTransaction(lastTxhex, new ApiMethods.RequestListener() {
                                            @Override
                                            public void onSuccess(Object response) {
                                                SendRawTxResponse res = (SendRawTxResponse) response;
                                                Log.d("TX_RES", "res " + res.getHashResult() + " error " + res.getError());
                                                closeProgress();
                                                showCongratsActivity();
                                            }
                                            @Override
                                            public void onFailure(String msg) {
                                                closeProgress();
                                                doToast(CurrencyUtils.getBtcLikeError(msg));
                                                Log.d("svcom", "failure - " + msg);
                                            }
                                        });
                                    } catch (ZCashException e) {
                                        closeProgress();
                                        doToast("Can not send the transaction to the node");
                                        Log.i("TX", "Cannot sign transaction");
                                    }
                                } else {
                                    closeProgress();
                                    doToast("Can not create the transaction. Check arguments");
                                    Log.i("psd", "createTransaction_taddr: RESPONSE CODE is not ok");
                                }
                            });
            } else {
                showError(etSumSend, getString(R.string.withdraw_amount_can_not_be_empty));
            }
        } catch (WrongNetworkException wne) {
            closeProgress();
            Log.e("psd", wne.toString());
            Toast.makeText(this, R.string.send_wrong_address, Toast.LENGTH_SHORT).show();
        } catch (ZCashException zce) {
            closeProgress();
            Toast.makeText(SendingCurrencyActivity.this, "Error: " + zce.getMessage(), Toast.LENGTH_SHORT).show();
            zce.printStackTrace();
        } catch (Exception e) {
            closeProgress();
            Toast.makeText(SendingCurrencyActivity.this, "Error of sending", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private boolean isAmountMoreBalance(String amount) {
        return false;
    }

    private void doToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SendingCurrencyActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCongratsActivity() {
        Intent intent = new Intent(this, CongratsActivity.class);
        intent.putExtra(Extras.CONGRATS_TEXT, getString(R.string.result_transaction_sent));
        intent.putExtra(Extras.COME_FROM, Extras.FROM_WITHDRAW);
        startActivity(intent);
    }

}

package com.guarda.ethereum.views.adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.guarda.ethereum.GuardaApp;
import com.guarda.ethereum.R;
import com.guarda.ethereum.managers.TransactionsManager;
import com.guarda.ethereum.managers.WalletManager;
import com.guarda.ethereum.rest.ApiMethods;
import com.guarda.ethereum.managers.EtherscanHelper;
import com.guarda.ethereum.models.constants.Extras;
import com.guarda.ethereum.models.items.TransactionResponse;
import com.guarda.ethereum.models.items.TransactionsListResponse;
import com.guarda.ethereum.views.activity.SendingCurrencyActivity;
import com.guarda.ethereum.utils.RepeatHandler;
import com.guarda.ethereum.utils.CalendarHelper;

import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;

import static com.guarda.ethereum.models.constants.Common.ETH_SHOW_PATTERN;

@AutoInjector(GuardaApp.class)
public class TransHistoryAdapter extends RecyclerView.Adapter<TransHistoryAdapter.TransHistoryItemHolder> {

    public interface OnItemClickListener {
        void OnItemClick(int position);
    }

    public static final int MIN_CONFIRMATIONS = 3;
    private static final int REPEAT_INTERVAL_MILLISECONDS = 6000;
    @Inject
    WalletManager walletManager;

    @Inject
    TransactionsManager transactionsManager;

    @Inject
    Context context;

    private ObjectAnimator loaderAnimation;
    private OnItemClickListener listener;

    private List<TransactionResponse> transList;
    private List<TransactionResponse> pendingList;
    private View view;
    private RepeatHandler repeatHandler;
    private Runnable onDataChangedCallback = null;

    public TransHistoryAdapter() {
        GuardaApp.getAppComponent().inject(this);
        this.transList = transactionsManager.getTransactionsList();
        transactionsManager.clearDuplicateTransactions();
        this.pendingList = transactionsManager.getPendingTransactions();
    }

    public void setOnDataChanged(Runnable callback) {
        onDataChangedCallback = callback;
    }

    private void fireOnDataCallback() {
        if (onDataChangedCallback != null)
            onDataChangedCallback.run();
    }

    private void startUpdateTask() {
        repeatHandler = new RepeatHandler(new Runnable() {
            @Override
            public void run() {
                EtherscanHelper.getTransactions(walletManager.getWalletFriendlyAddress(), new ApiMethods.RequestListener() {
                    @Override
                    public void onSuccess(Object response) {
                        TransactionsListResponse listResponse = (TransactionsListResponse) response;
                        List<TransactionResponse> transactionsLit = listResponse.getResult();
                        transactionsManager.setTransactionsList(transactionsLit);
                        transList = transactionsLit;
                        if (pendingList.isEmpty()) {
                            notifyDataSetChanged();
                            fireOnDataCallback();
                            repeatHandler.interrupt();
                        }
                    }

                    @Override
                    public void onFailure(String msg) {
                    }
                });
            }
        }, REPEAT_INTERVAL_MILLISECONDS);
        repeatHandler.setFirstRunImmediately(true);
        repeatHandler.start();
    }

    private void starCheckTxTask(final String txHash){
        if (repeatHandler != null) {
            repeatHandler.interrupt();
        }
        repeatHandler = new RepeatHandler(new Runnable() {
            @Override
            public void run() {
                EtherscanHelper.getTransactions(walletManager.getWalletFriendlyAddress(), new ApiMethods.RequestListener() {
                    @Override
                    public void onSuccess(Object response) {
                        TransactionsListResponse listResponse = (TransactionsListResponse) response;
                        List<TransactionResponse> transactionsList = listResponse.getResult();
                        for (TransactionResponse trans : transactionsList) {
                            if (trans.getHash().equals(txHash)){
                                if (Integer.parseInt(trans.getConfirmations()) >= MIN_CONFIRMATIONS){
                                    transactionsManager.setTransactionsList(transactionsList);
                                    transList = transactionsList;
                                    repeatHandler.interrupt();
                                    notifyDataSetChanged();
                                    fireOnDataCallback();
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(String msg) {
                    }
                });
            }
        }, REPEAT_INTERVAL_MILLISECONDS);
        repeatHandler.setFirstRunImmediately(true);
        repeatHandler.start();
    }

    public void setItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }


    @Override
    public TransHistoryItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_history, parent, false);
        return new TransHistoryItemHolder(view);
    }

    @Override
    public void onBindViewHolder(TransHistoryItemHolder holder, final int position) {
        final TransactionResponse item = getTxByPosition(position);

        holder.rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.OnItemClick(position);
                }
            }
        });

        if (item.isError()) {
//            enableRetryButton(holder);
            holder.viewIndicator.setBackground(view.getContext().getResources().getDrawable(R.drawable.transaction_indicator_red));
            holder.viewIndicator.setVisibility(View.VISIBLE);
            holder.tvTxStatus.setTextColor(view.getContext().getResources().getColor(R.color.txStatusRed));
            holder.tvTxStatus.setText(R.string.tx_status_fail);
//            holder.ivRetry.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    retryTransaction(item);
//                }
//            });
        } else if (item.getConfirmations() == null) {
//            enableLoader(holder);
            holder.tvTxStatus.setTextColor(view.getContext().getResources().getColor(R.color.txStatusGrey));
            holder.tvTxStatus.setText(R.string.tx_status_node);
//            holder.viewIndicator.setVisibility(View.INVISIBLE);
            if (repeatHandler == null || repeatHandler.isInterrupted()){
//                startUpdateTask();
            }
        } else if (Integer.parseInt(item.getConfirmations()) < MIN_CONFIRMATIONS) {
//            starCheckTxTask(item.getHash());
//            enableLoader(holder);
//            holder.viewIndicator.setVisibility(View.INVISIBLE);
            holder.tvTxStatus.setText(R.string.tx_status_wait);
        } else {
//            disableLoader(holder);
            holder.tvTxStatus.setVisibility(View.GONE);
            holder.viewIndicator.setBackground(view.getContext().getResources().getDrawable(R.drawable.transaction_indicator_green));
            holder.viewIndicator.setVisibility(View.VISIBLE);
        }

        BigInteger value = BigInteger.ZERO;
        try {
            value = new BigInteger(item.getValue());
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
        }
        BigDecimal decimal = new BigDecimal(value);
        BigDecimal formatted = Convert.fromWei(decimal, Convert.Unit.ETHER);
        if (item.getTicker() != null && item.getTicker().equalsIgnoreCase("NOW")) {
            formatted = formatted.multiply(new BigDecimal("10000000000"));
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setDecimalSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat(ETH_SHOW_PATTERN, symbols);
        decimalFormat.setRoundingMode(RoundingMode.DOWN);

        String valueStr = (isDebit(walletManager.getWalletFriendlyAddress(), item.getTo()) ? "+" : "-")
                + " " + decimalFormat.format(formatted) + " " + (item.getTicker() == null ? "" : item.getTicker());
        holder.tvTransactionSum.setText(valueStr);

//        if (isDebit(walletManager.getWalletFriendlyAddress(), item.getTo())) {
//            holder.viewIndicator.setBackground(view.getContext().getResources().getDrawable(R.drawable.transaction_indicator_green));
//        } else {
//            holder.viewIndicator.setBackground(view.getContext().getResources().getDrawable(R.drawable.transaction_indicator_red));
//        }

        holder.tvDate.setText(CalendarHelper.parseDateToddMMyyyy(item.getTimeStamp() * 1000));
//        holder.tvTokenTicker.setText(item.getTicker());
    }

    private void retryTransaction(TransactionResponse item) {
        String transValue = "";
        BigInteger value = new BigInteger(item.getValue());
        BigDecimal decimal = new BigDecimal(value);
        BigDecimal formatted = Convert.fromWei(decimal, Convert.Unit.ETHER);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setDecimalSeparator('.');
        DecimalFormat decimalFormat = new DecimalFormat(ETH_SHOW_PATTERN, symbols);
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        transValue = decimalFormat.format(formatted);

        Intent intent = new Intent(context, SendingCurrencyActivity.class);
        intent.putExtra(Extras.WALLET_NUMBER, item.getTo());
        intent.putExtra(Extras.AMOUNT_TO_SEND, transValue);
        if (item.getTicker() != null) {
            intent.putExtra(Extras.TOKEN_CODE_EXTRA, item.getTicker());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private TransactionResponse getTxByPosition(int position) {
        if (position + 1 <= pendingList.size()) {
            return pendingList.get(position);
        } else {
            return transList.get(position - pendingList.size());
        }
    }

//    private void enableRetryButton(TransHistoryItemHolder holder) {
//        holder.ivLoader.setVisibility(View.INVISIBLE);
//        holder.ivLoaderOuter.setVisibility(View.INVISIBLE);
//        holder.tvDate.setVisibility(View.INVISIBLE);
//        holder.ivRetry.setVisibility(View.VISIBLE);
//
//    }
//
//
//    private void enableLoader(TransHistoryItemHolder holder) {
//        holder.ivLoader.setVisibility(View.VISIBLE);
//        holder.ivLoaderOuter.setVisibility(View.VISIBLE);
//        holder.tvDate.setVisibility(View.INVISIBLE);
//        holder.ivRetry.setVisibility(View.INVISIBLE);
//        startClockwiseRotation(holder.ivLoader);
//        startCounterclockwiseRotation(holder.ivLoaderOuter);
//    }
//
//    private void disableLoader(TransHistoryItemHolder holder) {
//        holder.ivLoader.setVisibility(View.INVISIBLE);
//        holder.ivLoaderOuter.setVisibility(View.INVISIBLE);
//        holder.tvDate.setVisibility(View.VISIBLE);
//        holder.ivRetry.setVisibility(View.INVISIBLE);
//    }


    private void startClockwiseRotation(ImageView ivLoader) {
        loaderAnimation = ObjectAnimator.ofFloat(ivLoader, "rotation", 0.0f, 360f);
        loaderAnimation.setDuration(1500);
        loaderAnimation.setRepeatCount(ObjectAnimator.INFINITE);
        loaderAnimation.setInterpolator(new LinearInterpolator());
        loaderAnimation.start();
    }

    private void startCounterclockwiseRotation(ImageView ivLoader) {
        loaderAnimation = ObjectAnimator.ofFloat(ivLoader, "rotation", 360f, 0.0f);
        loaderAnimation.setDuration(1500);
        loaderAnimation.setRepeatCount(ObjectAnimator.INFINITE);
        loaderAnimation.setInterpolator(new LinearInterpolator());
        loaderAnimation.start();
    }

    private boolean isDebit(String ourAddress, String toAddress) {
        return ourAddress != null && ourAddress.equalsIgnoreCase(toAddress);
    }

    @Override
    public int getItemCount() {
        return transList.size() + pendingList.size();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (repeatHandler != null) {
            repeatHandler.interrupt();
        }
    }

    class TransHistoryItemHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.main_view)
        View rootView;
        @BindView(R.id.indicator)
        View viewIndicator;
        @BindView(R.id.tv_transaction_date)
        TextView tvDate;
        @BindView(R.id.tv_transaction_sum)
        TextView tvTransactionSum;
        @BindView(R.id.tv_tx_status)
        TextView tvTxStatus;
//        @BindView(R.id.iv_loader)
//        ImageView ivLoader;
//        @BindView(R.id.ic_outer_loader)
//        ImageView ivLoaderOuter;
//        @BindView(R.id.iv_retry)
//        ImageView ivRetry;
//        @BindView(R.id.tv_token_ticker)
//        TextView tvTokenTicker;


        TransHistoryItemHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
//            tvTokenTicker.setVisibility(View.VISIBLE);
        }
    }
}


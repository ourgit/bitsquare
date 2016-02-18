package io.bitsquare.btc.blockchain;

import com.google.common.util.concurrent.*;
import io.bitsquare.btc.blockchain.providers.FeeProvider;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.http.HttpException;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Timer;

class GetFeeRequest {
    private static final Logger log = LoggerFactory.getLogger(GetFeeRequest.class);
    private final ListeningExecutorService executorService;
    private Timer timer;
    private int faults;

    public GetFeeRequest() {
        executorService = Utilities.getListeningExecutorService("GetFeeRequest", 5, 10, 120L);
    }

    public SettableFuture<Coin> request(String transactionId, FeeProvider provider) {
        final SettableFuture<Coin> resultFuture = SettableFuture.create();
        return request(transactionId, provider, resultFuture);
    }

    private SettableFuture<Coin> request(String transactionId, FeeProvider provider, SettableFuture<Coin> resultFuture) {
        ListenableFuture<Coin> future = executorService.submit(() -> {
            Thread.currentThread().setName("requestFee-" + provider.toString());
            try {
                return provider.getFee(transactionId);
            } catch (IOException | HttpException e) {
                log.warn("Fee request failed for tx {} from provider {}\n error={}",
                        transactionId, provider, e.getMessage());
                throw e;
            }
        });

        Futures.addCallback(future, new FutureCallback<Coin>() {
            public void onSuccess(Coin fee) {
                log.info("Received fee of {}\nfor tx {}\nfrom provider {}", fee.toFriendlyString(), transactionId, provider);
                resultFuture.set(fee);
            }

            public void onFailure(@NotNull Throwable throwable) {
                if (timer == null) {
                    timer = UserThread.runAfter(() -> {
                        stopTimer();
                        faults++;
                        if (!resultFuture.isDone()) {
                            if (faults < 4) {
                                request(transactionId, provider, resultFuture);
                            } else {
                                resultFuture.setException(throwable);
                            }
                        } else {
                            log.debug("Got an error after a successful result. " +
                                    "That might happen when we get a delayed response from a timer request.");
                        }
                    }, 1 + faults);
                } else {
                    log.warn("Timer was not null");
                }
            }
        });

        return resultFuture;
    }

    private void stopTimer() {
        timer.cancel();
        timer = null;
    }
}
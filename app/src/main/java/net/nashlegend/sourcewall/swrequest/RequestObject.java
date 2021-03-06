package net.nashlegend.sourcewall.swrequest;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import net.nashlegend.sourcewall.request.RequestCache;
import net.nashlegend.sourcewall.swrequest.parsers.Parser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by NashLegend on 2015/9/23 0023.
 * 网络请求的对象
 */
public class RequestObject<T> {

    /**
     * 默认Tag
     */
    public static final String DefaultTag = "Default";

    private ResponseObject<T> responseObject = new ResponseObject<>();

    private int crtTime = 0;//当前重试次数

    protected int maxRetryTimes = 0;//最大重试次数

    protected int interval = 0;//重试间隔

    protected int requestType = RequestType.PLAIN;

    protected int method = Method.POST;

    protected HashMap<String, String> params = new HashMap<>();

    protected String url = "";

    protected CallBack<T> callBack = null;

    protected Parser<T> parser;

    protected Object tag = DefaultTag;

    protected String uploadParamKey = "file";

    protected MediaType mediaType = null;

    /**
     * 请求失败时是否使用缓存，如果为true，那么将使用缓存，请求成功的话也会将成功的数据缓存下来
     */
    protected boolean useCachedIfFailed = false;

    /**
     * 是否优先使用缓存，如果useCachedFirst为true，那么useCachedIfFailed就为false了
     * 仅仅在使用Rx时有效，与useCacheIfFailed互斥
     */
    protected boolean useCachedFirst = false;

    protected String filePath = "";

    protected boolean ignoreHandler = false;

    protected Handler handler;

    private Call call = null;

    /**
     * 生成此次请求的缓存key，
     * key的格式为：Method/{URL}/Params —— Params为a=b&c=d这样，按key排序
     *
     * @return
     */
    private String getCachedKey() {
        StringBuilder keyBuilder = new StringBuilder("");
        keyBuilder.append(method).append("/{").append(url).append("}/");
        if (params == null) {
            params = new HashMap<>();
        }
        if (params.size() > 0) {
            ArrayList<Map.Entry<String, String>> entryArrayList = new ArrayList<>();
            for (HashMap.Entry<String, String> entry : params.entrySet()) {
                if (TextUtils.isEmpty(entry.getKey())) {
                    continue;
                }
                entryArrayList.add(entry);
            }
            Collections.sort(entryArrayList, new Comparator<Map.Entry<String, String>>() {
                @Override
                public int compare(Map.Entry<String, String> lhs, Map.Entry<String, String> rhs) {
                    return lhs.getKey().compareTo(rhs.getKey());
                }
            });
            if (entryArrayList.size() > 0) {
                keyBuilder.append("?");
                for (int i = 0; i < entryArrayList.size(); i++) {
                    HashMap.Entry<String, String> entry = entryArrayList.get(i);
                    keyBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                keyBuilder.deleteCharAt(keyBuilder.length() - 1);
            }
        }
        return keyBuilder.toString();
    }

    /**
     * 在缓存中读取数据
     *
     * @return
     */
    private String readFromCache() {
        return RequestCache.getInstance().getStringFromCache(getCachedKey());
    }

    /**
     * 将数据存入缓存
     *
     * @return
     */
    private void saveToCache(String data) {
        if (shouldCache()) {
            RequestCache.getInstance().addStringToCacheForceUpdate(getCachedKey(), data);
        }
    }

    private boolean shouldCache() {
        return useCachedIfFailed || useCachedFirst;
    }

    @SuppressWarnings("unchecked")
    public void copyPartFrom(@NonNull RequestObject object) {
        try {
            if (object.params != null) {
                params = (HashMap<String, String>) object.params.clone();
            }
        } catch (Exception ignored) {

        }
        method = object.method;
        url = object.url;
        tag = object.tag;
        uploadParamKey = object.uploadParamKey;
        mediaType = object.mediaType;
    }

    /**
     * 异步请求，如果在enqueue执行之前就执行了cancel，那么将不会有callback执行，用户将不知道已经取消了请求。
     * 我们在请求中已经添加了synchronized，所以不考虑这种情况了
     */
    public void requestAsync() {
        handleHandler();
        switch (method) {
            case Method.GET:
                call = HttpUtil.getAsync(url, params, getInnerCallback(), tag);
                break;
            case Method.PUT:
                call = HttpUtil.putAsync(url, params, getInnerCallback(), tag);
                break;
            case Method.DELETE:
                call = HttpUtil.deleteAsync(url, params, getInnerCallback(), tag);
                break;
            default:
                call = HttpUtil.postAsync(url, params, getInnerCallback(), tag);
                break;
        }
    }

    /**
     * 同步请求
     *
     * @return
     */
    private Call requestSync() throws Exception {
        Call call;
        switch (method) {
            case Method.GET:
                call = HttpUtil.get(url, params, tag);
                break;
            case Method.PUT:
                call = HttpUtil.put(url, params, tag);
                break;
            case Method.DELETE:
                call = HttpUtil.delete(url, params, tag);
                break;
            default:
                call = HttpUtil.post(url, params, tag);
                break;
        }
        return call;
    }

    /**
     * 通过RxJava的方式招待请求，与requestAsync一样……
     */
    public void requestRx() {
        requestObservable().observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<ResponseObject<T>>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                if (callBack != null) {
                    callBack.onFailure(e, responseObject);
                }
            }

            @Override
            public void onNext(ResponseObject<T> tResponseObject) {
                if (callBack != null) {
                    callBack.onResponse(tResponseObject);
                }
            }
        });
    }

    /**
     * 异步请求，并不立即执行，仅仅返回Observable
     */
    public Observable<ResponseObject<T>> requestObservable() {
        return Observable
                .create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        try {
                            if (useCachedFirst) {
                                String cachedResult = readFromCache();
                                if (cachedResult != null) {
                                    responseObject.isCached = true;
                                    responseObject.body = cachedResult;
                                    subscriber.onNext(cachedResult);
                                    subscriber.onCompleted();
                                    return;
                                }
                            }
                            call = requestSync();
                            Response response = call.execute();
                            String result = response.body().string();
                            responseObject.statusCode = response.code();
                            responseObject.body = result;
                            if (response.isSuccessful()) {
                                subscriber.onNext(result);
                                subscriber.onCompleted();
                            } else {
                                subscriber.onError(new IllegalStateException("Not A Successful Response"));
                            }
                        } catch (Exception e) {
                            if (call != null && call.isCanceled()) {
                                responseObject.isCancelled = true;
                            }
                            subscriber.onError(e);
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .retryWhen(new RxRetryHandler())
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends String>>() {
                    @Override
                    public Observable<? extends String> call(Throwable throwable) {
                        if ((call == null || !call.isCanceled()) && useCachedIfFailed) {
                            //如果请求失败并且可以使用缓存，那么使用缓存
                            //只要不是取消掉的才会读取缓存，如果是取消掉的，读啥缓存啊，请求都没有了
                            String cachedResult = readFromCache();
                            if (cachedResult != null) {
                                responseObject.isCached = true;
                                responseObject.body = cachedResult;
                                return Observable.just(cachedResult);
                            }
                        }
                        JsonHandler.handleRequestException(throwable, responseObject);
                        return Observable.just("Error Occurred!");
                    }
                })
                .map(new Func1<String, ResponseObject<T>>() {
                    @Override
                    public ResponseObject<T> call(String string) {
                        if (responseObject.throwable == null && parser != null) {
                            try {
                                responseObject.result = parser.parse(string, responseObject);
                                if (responseObject.ok && !responseObject.isCached) {
                                    saveToCache(string);
                                }
                            } catch (Exception e) {
                                JsonHandler.handleRequestException(e, responseObject);
                            }
                        }
                        return responseObject;
                    }
                })
                .subscribeOn(Schedulers.computation());
    }

    public Subscription requestObservable(Subscriber<ResponseObject<T>> subscriber) {
        return requestObservable().observeOn(AndroidSchedulers.mainThread()).subscribe(subscriber);
    }

    public void uploadAsync() {
        handleHandler();
        HttpUtil.uploadAsync(url, params, uploadParamKey, mediaType, filePath, getInnerCallback());
    }

    private void handleHandler() {
        if (Thread.currentThread().getId() == 1) {
            //是果是在主线程请求,且handler为null，则将其置为在主线程执行callback
            if (!ignoreHandler && handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
        }
    }

    private Callback getInnerCallback() {
        return new Callback() {
            @Override
            synchronized public void onFailure(Request request, final IOException e) {
                final ResponseObject<T> responseObject = new ResponseObject<>();
                responseObject.requestObject = RequestObject.this;
                JsonHandler.handleRequestException(e, responseObject);
                onRequestFailure(e, responseObject);
            }

            @Override
            synchronized public void onResponse(Response response) throws IOException {
                final ResponseObject<T> responseObject = new ResponseObject<>();
                responseObject.requestObject = RequestObject.this;
                if (callBack != null && parser != null) {
                    try {
                        int statusCode = response.code();
                        String result = response.body().string();
                        responseObject.statusCode = statusCode;
                        responseObject.body = result;
                        if (response.isSuccessful()) {
                            responseObject.result = parser.parse(result, responseObject);
                            if (responseObject.ok) {
                                //如果请求成功且允许缓存，那么将此次请求的数据保存到缓存中
                                saveToCache(result);
                            }
                        } else {
                            if ((call == null || !call.isCanceled()) && useCachedIfFailed) {
                                //如果请求失败并且可以使用缓存，那么使用缓存
                                //只要不是取消掉的才会读取缓存，如果是取消掉的，读啥缓存啊，请求都没有了
                                String cachedResult = readFromCache();
                                if (cachedResult != null) {
                                    responseObject.isCached = true;
                                    responseObject.body = result;
                                    responseObject.result = parser.parse(cachedResult, responseObject);
                                }
                            }
                        }
                        if (handler != null) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (responseObject.ok) {
                                        callBack.onResponse(responseObject);
                                    } else {
                                        onRequestFailure(null, responseObject);
                                    }
                                }
                            });
                        } else {
                            if (responseObject.ok) {
                                callBack.onResponse(responseObject);
                            } else {
                                onRequestFailure(null, responseObject);
                            }
                        }
                    } catch (final Exception e) {
                        JsonHandler.handleRequestException(e, responseObject);
                        onRequestFailure(e, responseObject);
                    }
                }
            }
        };
    }

    /**
     * 异步请求出错
     *
     * @param e
     * @param result
     */
    private void onRequestFailure(final Exception e, final ResponseObject<T> result) {
        if (call != null && requestType == RequestType.PLAIN && shouldHandNotifier(e, result)) {
            if (interval > 0) {
                if (Thread.currentThread().getId() == 1) {
                    //如果在主线程
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            requestAsync();
                        }
                    }, interval);
                } else {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    } finally {
                        requestAsync();
                    }
                }
            } else {
                requestAsync();
            }
            notifyAction();
        } else {
            if (call != null && call.isCanceled()) {
                result.error = ResponseError.CANCELLED;
                result.isCancelled = true;
            }
            if (callBack != null) {
                if (handler != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callBack.onFailure(e, result);
                        }
                    });
                } else {
                    callBack.onFailure(e, result);
                }
            }
        }
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    public String dump() {
        StringBuilder err = new StringBuilder();
        err.append("    ").append("params").append(":").append(params).append("\n");
        err.append("    ").append("method").append(":").append(method).append("\n");
        err.append("    ").append("url").append(":").append(url).append("\n");
        err.append("    ").append("tag").append(":").append(tag).append("\n");
        if (requestType == RequestType.UPLOAD) {
            err.append("    ").append("uploadParamKey").append(":").append(uploadParamKey).append("\n");
            err.append("    ").append("mediaType").append(":").append(mediaType).append("\n");
        }
        return err.toString();
    }

    /**
     * http 请求方法
     */
    public interface Method {
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
    }

    /**
     * http 请求方法
     */
    public interface RequestType {
        int PLAIN = 0;
        int UPLOAD = 1;
        int DOWNLOAD = 2;
    }

    /**
     * http 请求回调
     *
     * @param <T>
     */
    public interface CallBack<T> {
        /**
         * result不可能为空
         *
         * @param e
         * @param result
         */
        void onFailure(@Nullable Throwable e, @NonNull ResponseObject<T> result);

        /**
         * 如果执行到此处，必然code==0,ok必然为true
         *
         * @param result
         */
        void onResponse(@NonNull ResponseObject<T> result);
    }

    public boolean shouldHandNotifier(Throwable exception, ResponseObject responseObject) {
        return responseObject.code != ResponseCode.CODE_TOKEN_INVALID
                && call != null
                && !call.isCanceled()
                && crtTime < maxRetryTimes
                && !(exception instanceof InterruptedIOException)
                && (responseObject.statusCode < 300 || responseObject.statusCode >= 500);
    }

    public void notifyAction() {
        crtTime++;
    }

    public class RxRetryHandler implements Func1<Observable<? extends Throwable>, Observable<?>> {

        @Override
        public Observable<?> call(Observable<? extends Throwable> observable) {
            return observable
                    .flatMap(new Func1<Throwable, Observable<?>>() {
                        @Override
                        public Observable<?> call(Throwable throwable) {
                            if (shouldHandNotifier(throwable, responseObject)) {
                                notifyAction();
                                return Observable.timer(maxRetryTimes, TimeUnit.MILLISECONDS);
                            } else {
                                return Observable.error(throwable);
                            }
                        }
                    });
        }
    }
}

package cn.wildfirechat.app;

import cn.wildfirechat.sdk.model.IMResult;
import com.google.gson.Gson;
import ikidou.reflect.TypeBuilder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;


public class HttpUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    static public  <T> T post(String url, Object object, Class<T> clazz) throws Exception{

        HttpPost post = null;
        try {
            HttpClient httpClient = HttpClientBuilder.create().build();

            post = new HttpPost(url);
            post.setHeader("Content-type", "application/json; charset=utf-8");
            post.setHeader("Connection", "Keep-Alive");

            String jsonStr;
            if (object instanceof String) {
                jsonStr = (String) object;
            } else {
                jsonStr = new Gson().toJson(object);
            }

            LOG.info("http request content: {}", jsonStr);

            StringEntity entity = new StringEntity(jsonStr, Charset.forName("UTF-8"));
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json");
            post.setEntity(entity);
            HttpResponse response = httpClient.execute(post);

            int statusCode = response.getStatusLine().getStatusCode();
            if(statusCode != HttpStatus.SC_OK){
                LOG.info("Request error: "+statusCode);
                throw new Exception("Http request error with code:" + statusCode);
            }else{
                BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity()
                        .getContent(),"utf-8"));
                StringBuffer sb = new StringBuffer();
                String line;
                String NL = System.getProperty("line.separator");
                while ((line = in.readLine()) != null) {
                    sb.append(line + NL);
                }

                in.close();

                String content = sb.toString();
                LOG.info("http request response content: {}", content);

                return new Gson().fromJson(content, clazz);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }finally{
            if(post != null){
                post.releaseConnection();
            }
        }
    }

    private static <T> IMResult<T> fromJsonObject(String content, Class<T> clazz) {
        Type type = TypeBuilder
                .newInstance(IMResult.class)
                .addTypeParam(clazz)
                .build();
        return new Gson().fromJson(content, type);
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

}

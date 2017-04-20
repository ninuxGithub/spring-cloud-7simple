package cloud.eureka.server.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSON;

/**
 * 解决httpClient对https请求报不支持SSLv3问题. JDK_HOME/jrebcurity/java.security 文件中注释掉：
 * jdk.certpath.disabledAlgorithms=MD2
 * jdk.tls.disabledAlgorithms=DSA(或jdk.tls.disabledAlgorithms=SSLv3)
 */
public class HttpsUtil {
	public static CloseableHttpClient createClient() throws Exception {
		TrustStrategy trustStrategy = new TrustStrategy() {
			@Override
			public boolean isTrusted(X509Certificate[] xc, String msg) {
				return true;
			}
		};
		SSLContextBuilder builder = new SSLContextBuilder();
		builder.loadTrustMaterial(trustStrategy);
		HostnameVerifier hostnameVerifierAllowAll = new HostnameVerifier() {
			@Override
			public boolean verify(String name, SSLSession session) {
				return true;
			}
		};
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
				new String[] { "SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2" }, null, hostnameVerifierAllowAll);

		HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {
			public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
				// 重试设置
				if (executionCount >= 5) {
					// Do not retry if over max retry count
					return false;
				}
				if (exception instanceof InterruptedIOException) {
					// Timeout
					return false;
				}
				if (exception instanceof UnknownHostException) {
					// Unknown host
					return false;
				}
				if (exception instanceof ConnectTimeoutException) {
					// Connection refused
					return false;
				}
				if (exception instanceof SSLException) {
					// SSL handshake exception
					return false;
				}
				HttpClientContext clientContext = HttpClientContext.adapt(context);
				HttpRequest request = clientContext.getRequest();
				boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
				if (idempotent) {
					return true;
				}
				return false;
			}
		};
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(120000).setSocketTimeout(120000)// 超时设置
				.build();
		CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).setRetryHandler(myRetryHandler)// 重试设置
				.setDefaultRequestConfig(requestConfig).build();
		return httpclient;
	}

	public static String get(String url) throws Exception {
		return get(url, null, null);
	}

	public static String get(String url, Map<String, String> header, Map<String, String> outCookies) throws Exception {
		String body = "";
		String Encoding = "utf-8";
		CloseableHttpClient client = createClient();
		try {
			CookieStore cookieStore = new BasicCookieStore();
			HttpClientContext localContext = HttpClientContext.create();
			localContext.setCookieStore(cookieStore);
			// 创建get方式请求对象
			HttpGet httpGet = new HttpGet(url);
			if (header != null) {
				if (header.get("Accept") != null)
					httpGet.setHeader("Accept", header.get("Accept"));
				if (header.get("Cookie") != null)
					httpGet.setHeader("Cookie", header.get("Cookie"));
				if (header.get("Accept-Encoding") != null)
					httpGet.setHeader("Accept-Encoding", header.get("Accept-Encoding"));
				if (header.get("Accept-Language") != null)
					httpGet.setHeader("Accept-Language", header.get("Accept-Language"));
				if (header.get("Host") != null)
					httpGet.setHeader("Host", header.get("Host"));
				if (header.get("User-Agent") != null)
					httpGet.setHeader("User-Agent", header.get("User-Agent"));
				if (header.get("x-requested-with") != null)
					httpGet.setHeader("x-requested-with", header.get("x-requested-with"));
				if (header.get("Encoding") != null)
					Encoding = header.get("Encoding");
			}
			System.out.println("请求地址：" + url);
			// 执行请求操作，并拿到结果（同步阻塞）
			CloseableHttpResponse response = client.execute(httpGet, localContext);
			// 获取结果实体
			try {
				// 如果需要输出cookie
				if (outCookies != null) {
					List<Cookie> cookies = cookieStore.getCookies();
					for (int i = 0; i < cookies.size(); i++) {
						outCookies.put(cookies.get(i).getName(), cookies.get(i).getValue());
					}
				}
				HttpEntity entity = response.getEntity();
				System.out.println("返回：" + response.getStatusLine());
				if (entity != null) {
					// 按指定编码转换结果实体为String类型
					body = EntityUtils.toString(entity, Encoding);
					// System.out.println("返回："+body);
				}
			} finally {
				response.close();
			}
		} finally {
			client.close();
		}
		return body;
	}

	public static String post(String url, Map<String, String> params) throws Exception {
		return post(url, params, null, null);
	}

	public static String post(String url, Map<String, String> params, Map<String, String> header,
			Map<String, String> outCookies) throws Exception {
		String body = "";
		String encoding = "utf-8";
		String contentType = "text/html";
		CloseableHttpClient client = createClient();
		CookieStore cookieStore = new BasicCookieStore();
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setCookieStore(cookieStore);
		try {
			// 创建post方式请求对象
			HttpPost httpPost = new HttpPost(url);
			if (header != null) {
				if (header.get("Accept") != null)
					httpPost.setHeader("Accept", header.get("Accept"));
				if (header.get("Cookie") != null)
					httpPost.setHeader("Cookie", header.get("Cookie"));
				if (header.get("Accept-Encoding") != null)
					httpPost.setHeader("Accept-Encoding", header.get("Accept-Encoding"));
				if (header.get("Accept-Language") != null)
					httpPost.setHeader("Accept-Language", header.get("Accept-Language"));
				if (header.get("Host") != null)
					httpPost.setHeader("Host", header.get("Host"));
				if (header.get("User-Agent") != null)
					httpPost.setHeader("User-Agent", header.get("User-Agent"));
				if (header.get("x-requested-with") != null)
					httpPost.setHeader("x-requested-with", header.get("x-requested-with"));
				if (header.get("Encoding") != null)
					encoding = header.get("Encoding");
				if (header.get("Content-Type") != null)
					contentType = header.get("Content-Type");
			}
			// 装填参数
			if (contentType.equalsIgnoreCase("text/html")) {
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				if (params != null) {
					for (Entry<String, String> entry : params.entrySet()) {
						nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
					}
				}
				httpPost.setEntity(new UrlEncodedFormEntity(nvps, encoding));
			}
			// JOSN格式参数
			if (contentType.equalsIgnoreCase("application/json")) {
				StringEntity myEntity = new StringEntity(JSON.toJSONString(params.get("data")),
						ContentType.create("application/json", "UTF-8"));
				httpPost.setEntity(myEntity);
			}
			System.out.println("请求地址：" + url);
			// 执行请求操作，并拿到结果（同步阻塞）
			CloseableHttpResponse response = client.execute(httpPost, localContext);
			// 获取结果实体
			try {
				// 如果需要输出cookie
				if (outCookies != null) {
					List<Cookie> cookies = cookieStore.getCookies();
					for (int i = 0; i < cookies.size(); i++) {
						outCookies.put(cookies.get(i).getName(), cookies.get(i).getValue());
					}
				}
				HttpEntity entity = response.getEntity();
				System.out.println("返回：" + response.getStatusLine());
				if (entity != null) {
					// 按指定编码转换结果实体为String类型
					body = EntityUtils.toString(entity, encoding);
					// System.out.println("返回："+body);
				}
			} finally {
				response.close();
			}
		} finally {
			client.close();
		}
		return body;
	}

	public static void main(String[] args) throws Exception {
		String body = get("https://www.baidu.com/");
		System.out.println(body);
	}
}

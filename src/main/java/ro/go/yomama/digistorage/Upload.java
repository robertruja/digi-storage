package ro.go.yomama.digistorage;

import org.crumbs.core.annotation.Crumb;
import org.crumbs.core.annotation.CrumbInit;
import org.crumbs.core.annotation.Property;
import org.crumbs.json.JsonMapper;
import org.crumbs.json.TypeReference;
import org.crumbs.json.exception.JsonMarshalException;
import ro.go.yomama.digistorage.model.Auth;
import ro.go.yomama.digistorage.model.FileList;
import ro.go.yomama.digistorage.model.FileUploadResponse;
import ro.go.yomama.digistorage.model.UploadLink;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Consumer;

@Crumb
public class Upload {

    private static final String LINE_FEED = "\r\n";

    @Property("base.url")
    private String baseUrl;

    @Property("auth.path")
    private String authUrl;

    @Property("link.path")
    private String getLinkUrlPath;

    @Property("list.files")
    private String listFilesPath;

    @Property("upload.path")
    private String uploadPath;

    @Property("delete.path")
    private String deletePath;

    @Property("target.file.path")
    private String targetFilePath;

    @Property("login.apikey")
    private String apiKey;

    @Property("login.username")
    private String username;

    private JsonMapper mapper = new JsonMapper();

    private File uploadFile;

    @CrumbInit
    private void init() {
        uploadFile = new File(targetFilePath);
        if(!uploadFile.exists()) {
            throw new RuntimeException("No file at path: " + targetFilePath);
        }
    }

    public void run() {
        String token = authenticate();
        String uploadLink = getUploadLink(token);
        FileList files = listFiles(token);
        if(files.getFiles().stream().anyMatch(file -> file.getName().equals(uploadFile.getName()))) {
            deleteExisting(token);
        }
        uploadFile(uploadLink);
    }

    private String authenticate() {
        Auth.Response response = performReq("Authentication",
                baseUrl + authUrl,
                null,
                Auth.Response.class,
                conn -> {
                    try {
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestMethod("POST");
                        OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                        writer.write(mapper.marshal(new Auth.Request(username, apiKey)));
                        writer.close();
                    } catch (IOException | JsonMarshalException | IllegalAccessException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        return response.getToken();
    }

    private String getUploadLink(String token) {
        UploadLink.Response response = performReq("Get upload link",
                baseUrl + getLinkUrlPath,
                null,
                UploadLink.Response.class,
                conn -> {
                    try {
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Authorization", "Token token=\"" + token + "\"");
                    } catch (ProtocolException e) {
                        throw new RuntimeException(e);
                    }
                });
        return response.getLink();
    }

    private FileList listFiles(String token) {
        return performReq("List files", baseUrl + listFilesPath, null, FileList.class, conn -> {
            try {
                conn.setRequestMethod("GET");
                conn.addRequestProperty("Accept", "application/json");
                conn.addRequestProperty("Authorization", "Token token=\"" + token + "\"");
            } catch (ProtocolException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void deleteExisting(String token) {
        performReq("Delete file", baseUrl + deletePath + uploadFile.getName(), null, null, conn -> {
            try {
                conn.setRequestMethod("DELETE");
                conn.addRequestProperty("Accept", "application/json");
                conn.addRequestProperty("Content-Length", "0");
                conn.addRequestProperty("Authorization", "Token token=\"" + token + "\"");
            } catch (ProtocolException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void uploadFile(String uploadLink) {
        List<FileUploadResponse> response = performReq("Upload file",
                uploadLink,
                new TypeReference<List<FileUploadResponse>>(){},
                null,
                conn -> {
                    try {
                        conn.setRequestMethod("POST");
                        conn.addRequestProperty("Accept", "*/*");
                        String boundary = "BOUNDARYBOUNDARYBOUNDARYBOUNDARY";
                        conn.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                        conn.addRequestProperty("Content-Length", "" + uploadFile.length());
                        PrintWriter writer;
                        OutputStream os = conn.getOutputStream();
                        writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
                        String fileName = uploadFile.getName();
                        writer.append("--" + boundary).append(LINE_FEED);
                        writer.append(
                                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"")
                                .append(LINE_FEED);
                        writer.append(
                                "Content-Type: application/octet-stream")
                                .append(LINE_FEED);
                        writer.append(LINE_FEED);
                        writer.flush();

                        FileInputStream inputStream = new FileInputStream(uploadFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        inputStream.close();
                        writer.append(LINE_FEED);
                        writer.append("--" + boundary + "--").append(LINE_FEED);
                        writer.append(LINE_FEED);
                        writer.flush();
                        writer.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                );
        System.out.println(response);
    }

    static {
        TrustManager[] trustAllCertificates = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null; // Not relevant.
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing. Just allow them all.
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Do nothing. Just allow them all.
                    }
                }
        };

        HostnameVerifier trustAllHostnames = (hostname, session) -> {
            return true; // Just allow them all.
        };

        try {
            System.setProperty("jsse.enableSNIExtension", "false");
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCertificates, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private <T> T performReq(String operation, String url, TypeReference<T> responseTypeRef, Class<T> clazz,
                                Consumer<HttpURLConnection> requestProcessor) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setDoOutput(true);
            requestProcessor.accept(conn);

            if (conn.getResponseCode() != 200) {
                System.out.println("Error during " + operation + " operation, response code: " + conn.getResponseCode());
                throw new RuntimeException("Unable to perform " + operation);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                if(responseTypeRef != null) {
                    return mapper.unmarshal(response.toString().getBytes(), responseTypeRef);
                } else if(clazz != null) {
                    return mapper.unmarshal(response.toString().getBytes(), clazz);
                }
                return null;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

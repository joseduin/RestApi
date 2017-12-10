package foxhound.postman;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ByteArrayEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.rootMain) CoordinatorLayout rootMain;

    // Request
    @BindView(R.id.combo_request) Spinner combo_request;
    @BindView(R.id.url_request) EditText url_request;
    @BindView(R.id.token_request) EditText token_request;
    @BindView(R.id.bodyContent) LinearLayout bodyContent;
    @BindView(R.id.body_request) EditText body_request;
    @BindView(R.id.boton_request) Button boton_request;

    // Response
    @BindView(R.id.response_content) RelativeLayout response_content;
    @BindView(R.id.status) TextView status;
    @BindView(R.id.body_response) EditText body_response;
    @BindView(R.id.token_response) EditText token_response;
    @BindView(R.id.copy_it) Button copy_it;

    @BindString(R.string.heroku_url) String heroku_url;
    @BindString(R.string.auth) String auth;

    private ProgressDialog progressDialog;

    private AsyncHttpClient client = new AsyncHttpClient();
    private RequestParams parametros = new RequestParams();
    private ByteArrayEntity entity = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        loarSpinner();
        loadUrl();
        visibleViews();
    }

    /**
     *  Ocultamos algunas componentes
     **/
    private void visibleViews() {
        bodyContent.setVisibility(View.GONE);
        response_content.setVisibility(View.GONE);
    }

    /**
     *  Cargamos con la url defecto de heroku
     **/
    private void loadUrl() {
        url_request.setText(heroku_url);
    }

    /**
     *  Cargamos el combo de tipos de requests
     **/
    private void loarSpinner() {
        List<String> type_request = new ArrayList<>();
        type_request.add("GET");
        type_request.add("POST");
        type_request.add("PUT");
        type_request.add("DELETE");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, type_request);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        combo_request.setAdapter(dataAdapter);
    }

    /**
     *  Depende de lo seleccionado en el cambo, ocultamos o mostramos el campo body
     **/
    @OnItemSelected(R.id.combo_request) public void changeComboRequest(Spinner spinner, int position) {
        switch (spinner.getItemAtPosition(position).toString()) {
            case "POST":
            case "PUT":
                bodyContent.setVisibility(View.VISIBLE);
                break;
            default:
                bodyContent.setVisibility(View.GONE);
                break;
        }
    }

    /**
     *  Action Listener del Boton 'Send' Request
     **/
    @OnClick(R.id.boton_request) public void doRequest() {
        client = new AsyncHttpClient();
        parametros = new RequestParams();
        validateHeader();
        limpiarResponse();

        String url = url_request.getText().toString();
        if (url.isEmpty()) {
            message("Ingrese una URL");
            return;
        }
        prepareDialogProgress();
        response_content.setVisibility(View.VISIBLE);

        switch (combo_request.getSelectedItem().toString()) {
            case "GET":
                get_method(url);
                break;
            case "POST":
                post_method(url);
                break;
            case "PUT":
                put_method(url);
                break;
            case "DELETE":
                delete_method(url);
                break;
        }
    }

    /**
     *  Limpiamos los campos del response
     **/
    private void limpiarResponse() {
        status.setText("");
        body_response.setText("");
        token_response.setText("");
    }

    /**
     *  Si ingresamos el Token en el request, entonces lo
     *  mandamos en el Header del Request
     **/
    private void validateHeader() {
        String token = token_request.getText().toString();
        if (!token.isEmpty())
            client.addHeader(auth, token);
    }

    /**
     *  Si estamos en POST o PUT debemos validar que se ingrese un Body
     *  para el request
     **/
    private void validateBody() {
        String body = body_request.getText().toString();
        if (body.isEmpty()) {
            message("Ingrese el cuerpo de la petición");
            return;
        }
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(body);
        JsonObject jo = je.getAsJsonObject();

        try {
            entity = new ByteArrayEntity(jo.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        entity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

        client.addHeader("Content-Type", "application/json");
    }

    /**
     *  GET Method
     **/
    private void get_method(String url) {
        client.get(url, parametros, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int i, Header[] headers, String s, Throwable throwable) {
                onFailureRequest(i);
            }

            @Override
            public void onSuccess(int i, Header[] headers, String s) {
                onSuccessRequest(i, s);

                for (Header h: headers) {
                    if (h.getName().equals(auth)) {
                        token_response.setText(h.getValue());
                        break;
                    }
                }
            }
        });
    }

    /**
     *  POST Method
     **/
    private void post_method(String url) {
        validateBody();

        client.post(this.getApplicationContext(), url, entity, "application/json", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int i, Header[] headers, String s, Throwable throwable) {
                onFailureRequest(i);
            }

            @Override
            public void onSuccess(int i, Header[] headers, String s) {
                onSuccessRequest(i, s);
            }
        });
    }

    /**
     *  PUT Method
     **/
    private void put_method(String url) {
        validateBody();

        client.put(this.getApplicationContext(), url, entity, "application/json", new TextHttpResponseHandler() {
            @Override
            public void onFailure(int i, Header[] headers, String s, Throwable throwable) {
                onFailureRequest(i);
            }

            @Override
            public void onSuccess(int i, Header[] headers, String s) {
                onSuccessRequest(i, s);
            }
        });
    }

    /**
     *  DELETE Method
     **/
    private void delete_method(String url) {
        client.put(url, parametros, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int i, Header[] headers, String s, Throwable throwable) {
                onFailureRequest(i);
            }

            @Override
            public void onSuccess(int i, Header[] headers, String s) {
                onSuccessRequest(i, s);
            }
        });
    }

    /**
     *  Error Request
     **/
    private void onFailureRequest(int code) {
        status.setText(String.valueOf(code));
        progressDialog.dismiss();
        message("Error de conexion");
    }

    /**
     *  Mostramos datos de response
     **/
    private void onSuccessRequest(int code, String response) {
        status.setText(String.valueOf(code));
        body_response.setText( toPrettyFormat(response) );
        progressDialog.dismiss();
    }

    /**
     *  Json Format
     **/
    private String toPrettyFormat(String jsonString) {
        String prettyJson = "";
        try {
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(jsonString).getAsJsonObject();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            prettyJson = gson.toJson(json);
        } catch (Exception e) {
            prettyJson = jsonString;
        }
        return prettyJson;
    }

    /**
     *  Copiamos lo que hay en la caja de token
     **/
    @OnClick(R.id.copy_it) public void doCopyIt() {
        String token = token_response.getText().toString().trim();

        ClipData clip = ClipData.newPlainText("text", token);
        ClipboardManager clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);

        message("Token copiado al portapapeles");
    }

    /**
     *  Creamos un mensaje tipo Snack
     **/
    private void message(String m) {
        Snackbar.make(rootMain, m, Snackbar.LENGTH_LONG).show();
    }

    /**
     *  Creamos un Progress Dialog
     **/
    private void prepareDialogProgress() {
        this.progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Consultando...");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

}

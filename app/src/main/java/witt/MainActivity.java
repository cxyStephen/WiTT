package witt;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.samples.hellosceneform.R;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;

    private Map<Integer, Node> nodeMap;
    private int counter;

    private CloudVisionAPI vision;
    private CloudTranslateAPI trans;
    private DynamoDbAPI dynamoDB;

    private Spinner spinner1, spinner2;
    private String from = "English";
    private String to = "Chinese (中文)";

    private DynamoDBMapper dynamoClient;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        AWSMobileClient.getInstance().initialize(this).execute();

        AWSCredentialsProvider credentialsProvider = AWSMobileClient.getInstance().getCredentialsProvider();
        AWSConfiguration configuration = AWSMobileClient.getInstance().getConfiguration();

        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(credentialsProvider);

        dynamoClient = DynamoDBMapper.builder()
                .dynamoDBClient(dynamoDBClient)
                .awsConfiguration(configuration)
                .build();

        dynamoDB = new DynamoDbAPI(dynamoClient);

        vision = new CloudVisionAPI();
        trans = new CloudTranslateAPI();

        nodeMap = new HashMap<>();

        setContentView(R.layout.activity_ux);
        Spinner staticSpinner = findViewById(R.id.spinnerFrom);
        ArrayAdapter<CharSequence> staticAdapter = ArrayAdapter
                .createFromResource(this, R.array.From,
                        android.R.layout.simple_spinner_item);
        staticAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        staticSpinner.setAdapter(staticAdapter);
        staticSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                from = parent.getItemAtPosition(position).toString();

            }
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        //second drop down
        Spinner staticSpinner2 = findViewById(R.id.spinnerTo);
        ArrayAdapter<CharSequence> staticAdapter2 = ArrayAdapter
                .createFromResource(this, R.array.To,
                        android.R.layout.simple_spinner_item);
        staticAdapter2
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        staticSpinner2.setAdapter(staticAdapter2);

        staticSpinner2.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                to = parent.getItemAtPosition(position).toString();

            }
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        if (arFragment != null) {
            arFragment.getPlaneDiscoveryController().hide();
            arFragment.getPlaneDiscoveryController().setInstructionView(null);
            arFragment.getArSceneView().getPlaneRenderer().setEnabled(false);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN && y < 1920 - 150) {
            int nodeId = showBlackBox();
            takePhoto(nodeId, x, y);
        }
        return super.dispatchTouchEvent(event);
    }

    private int showBlackBox() {
        Log.d("Touch Me", "Stop touching me dude");

        ViewRenderable.builder()
                .setView(this, R.layout.text)
                .build()
                .thenAccept(viewRenderable -> {
                    Vector3 forward = arScene().getCamera().getForward();
                    Vector3 cameraPosition = arScene().getCamera().getWorldPosition();
                    Vector3 position = Vector3.add(cameraPosition, forward);
                    Quaternion rotation = arScene().getCamera().getLocalRotation();
                    //Vector3 direction = Vector3.subtract(cameraPosition, forward);
                    //direction.y = position.y;
                    float[] pos = {position.x, position.y, -0.25f};
                    float[] rot = {rotation.x, rotation.y, rotation.z, rotation.w};
                    AnchorNode anchor = new AnchorNode(arFragment.getArSceneView()
                            .getSession().createAnchor(new Pose(pos, rot)));
                    //AnchorNode anchor = new AnchorNode();
                    //anchor.setWorldPosition(position);
                    //anchor.setLookDirection(direction);
                    Log.d("Touch Me", "hi");
                    Node node = new Node();
                    node.setRenderable(viewRenderable);
                    anchor.setParent(arScene());
                    node.setParent(anchor);
                    nodeMap.put(counter, node);
                    Log.d("WTF", "" + nodeMap.keySet().size());
                    counter++;
                }).exceptionally(
                throwable -> {
                    Log.d("Touch Me", "oops" + throwable.getMessage());
                    return null;
                });
        return counter;
    }

    private void updateNode(int id, String str1, String str2) {
        Log.d("WTF2", id + "\t" + nodeMap.keySet().toString());

        Node n = nodeMap.get(id);


        String newStr = str1 + "\n" + str2;

        ViewRenderable.builder()
                .setView(this, R.layout.transparent)
                .build()
                .thenAccept(viewRenderable -> {
                    ((TextView) viewRenderable.getView().findViewById(R.id.text)).setText(newStr);
                    n.setRenderable(viewRenderable);
                }).exceptionally(
                throwable -> {
                    Log.d("Touch Me", "oops" + throwable.getMessage());
                    return null;
                });
    }

    private Scene arScene() {
        return arFragment.getArSceneView().getScene();
    }

    private void takePhoto(int id, float x, float y) {
        ArSceneView view = arFragment.getArSceneView();

        // Create a bitmap the size of the scene view.
        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                TouchEvent te = generateTouchEvent(id, bitmap, x, y);
                new TapTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, te);
            } else {
                Toast toast = Toast.makeText(MainActivity.this,
                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                toast.show();
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
    }

    private TouchEvent generateTouchEvent(int id, Bitmap bitmap, float x, float y) {
        return new TouchEvent(id, bitmap, 1080, 1920, x, y);
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private class TapTask extends AsyncTask<TouchEvent, Void, String[]> {

        @Override
        protected String[] doInBackground(TouchEvent... objects) {
            Log.d("fuck my life", from);
            Log.d("fuck my life", to);
            String lang1 = from;
            String lang2 = to;

            if(!lang1.equals("English"))
                lang1 = lang1.substring(0, from.indexOf(" "));
            if(!lang2.equals("English"))
                lang2 = lang2.substring(0, to.indexOf(" "));

            TouchEvent event = objects[0];
            String out = vision.processImage(event);
            String text1 = dynamoDB.getTranslation(lang1, out);
            String text2 = dynamoDB.getTranslation(lang2, out);
            if(text1 == null) {
                text1 = trans.translate(out, "en", trans.getCode(lang1));
                dynamoDB.createTranslation(lang1, out, text1);
            }
            if(text2 == null) {
                text2 = trans.translate(out, "en", trans.getCode(lang2));
                dynamoDB.createTranslation(lang2, out, text2);
            }
            return new String[]{String.valueOf(event.getId()), text1, text2};
        }

        @Override
        protected void onPostExecute(String[] out) {
            int id = Integer.parseInt(out[0]);
            updateNode(id, out[1], out[2]);
        }
    }
}

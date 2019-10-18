package com.example.petshion_final;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.petshion_final.arcore.render.RenderHandler;
import com.example.petshion_final.arcore.render.RenderHandlerCustom;
import com.example.petshion_final.permission.Permission;
import com.example.petshion_final.tensorflow.lite.Classifier;
import com.example.petshion_final.tensorflow.lite.TensorFlowInterpreterGpu;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    //ar required variables
    private ArFragment arFragment;
    private RenderHandler renderHandler;
    private boolean shouldTakePhoto = true;
    //Tensorflow required variables
    private static final String MODEL_PATH = "bbs_dog.tflite";
    private static final boolean QUANT = false;
    private static final String LABEL_PATH =  "labels_bbs.txt";
    private static int INPUT_SIZE = 224;
    private Classifier classifier;
    private Executor executor  =  Executors.newSingleThreadExecutor();

    //UI variables
    private BottomSheetBehavior sheetBehavior;
    private LinearLayout bottomSheet;
    private Button bottomButton;
    private TextView mMedicineName;

    private Permission permission;

    // 3d model
    private ModelRenderable glassesRenderable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // lock screen rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // permission check
        permission = new Permission(this);
        boolean t = permission.checkPermissions();

        Log.d(TAG, "permission check: " + t);

        initTensorFlowAndLoadModel();
        bottomButton = findViewById(R.id.bottom_btn);
//        bottomSheet = findViewById(R.id.bottom_sheet);
        mMedicineName = findViewById(R.id.medicine_name);
//        sheetBehavior = BottomSheetBehavior.from(bottomSheet);

        ModelRenderable.builder()
                .setSource(this, Uri.parse("glasses01.sfb"))
                .build()
                .thenAccept(renderable -> glassesRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this,
                                            "Unable to load andy renderable",
                                            Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                /**
                 *
                 * Test code
                 */
            }
        });


        // render = new RenderHandler(MainActivity.this);
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
        arFragment.getArSceneView().getScene().setOnTouchListener(new Scene.OnTouchListener() {

            @Override
            public boolean onSceneTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
                if (glassesRenderable == null) {
                    return false;
                }



                RenderHandlerCustom.placeGlassesTest(glassesRenderable, 0);

                return true;
            }
        });


        // if see plane, place object.
        arFragment.setOnTapArPlaneListener((HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
            if (glassesRenderable == null) {
                return;
            }
            Log.d(TAG, "onTap : " + motionEvent.getAction());
        });

        // must last set arFragment
        RenderHandlerCustom.setArFragment(arFragment);
    }





    private void onUpdateFrame(FrameTime frameTime) {
        long UpdateStartTime = System.nanoTime();

        Log.d(TAG, "fps: " + (1. / frameTime.getDeltaSeconds()));
        Session session = arFragment.getArSceneView().getSession();

        Config config = new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
        Frame frame = arFragment.getArSceneView().getArFrame();

        //if there is no frame don't process anything
        if(frame == null){
            return;
        }
        // If Arcore is not tracking yet then don't process anything
        if(frame.getCamera().getTrackingState() != TrackingState.TRACKING){
            return;
        }


        //if ARCore is tracking get start processing
        if(frame.getCamera().getTrackingState() ==  TrackingState.TRACKING) {
            if (shouldTakePhoto) {

                try {
                    //take photo convert photo to Bitmap format so that it could be use in the TensorflowImageClassifierClass for detection
                    Image img = frame.acquireCameraImage();
                    byte[] nv21;
                    ContextWrapper cw = new ContextWrapper(getApplicationContext());
                    String fileName = "test.jpg";
                    File dir = cw.getDir("imageDir", Context.MODE_PRIVATE);
                    File file = new File(dir, fileName);
                    FileOutputStream outputStream;
                    try {

                        long transferstartTime = System.nanoTime();
                        Log.d(TAG, "transfer start time : " + (transferstartTime - UpdateStartTime));
                        outputStream = new FileOutputStream(file);
                        ByteBuffer yBuffer = img.getPlanes()[0].getBuffer();
                        ByteBuffer uBuffer = img.getPlanes()[1].getBuffer();
                        ByteBuffer vBuffer = img.getPlanes()[2].getBuffer();

                        int ySize = yBuffer.remaining();
                        int uSize = uBuffer.remaining();
                        int vSize = vBuffer.remaining();

                        nv21 = new byte[ySize + uSize + vSize];

                        yBuffer.get(nv21, 0, ySize);
                        vBuffer.get(nv21, ySize, vSize);
                        uBuffer.get(nv21, ySize + vSize, uSize);

                        int width = img.getWidth();
                        int height = img.getHeight();

                        img.close();


                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                        byte[] byteArray = out.toByteArray();

                        Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);
                        if (bitmap != null) {
                            Log.i("bitmap ", "contains data");

                        }
                        Bitmap portraitBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);


                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(portraitBitmap, INPUT_SIZE, INPUT_SIZE, false);
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        outputStream.flush();
                        outputStream.close();

                        long trasferFinishedTime = System.nanoTime();
                        Log.d(TAG, "transfer finished time: " + (trasferFinishedTime - transferstartTime));
                        List<Classifier.Recognition> results = classifier.recognizeImage(scaledBitmap);

                        long MLTestingTIme = System.nanoTime();
                        Log.d(TAG, "MLTestingTime: " + (MLTestingTIme - trasferFinishedTime));
                        for (Classifier.Recognition r : results) {
                            Log.d(TAG, r.getTitle() + r.getConfidence());
                        }

                        /**
                         *
                         * TODO
                         * render dots
                         */

                        //change according with your model
                        /**
                         *
                         * TODO
                         * land mark and find directions
                         */


                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (NotYetAvailableException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(TAG, "one recyle time: " + (System.nanoTime() - UpdateStartTime));
    }

    private void initTensorFlowAndLoadModel(){
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    classifier = TensorFlowInterpreterGpu.create(
                            getAssets(),
                            MODEL_PATH,
                            LABEL_PATH,
                            INPUT_SIZE,
                            QUANT);
                }catch(IOException e){
                    e.printStackTrace();
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

}

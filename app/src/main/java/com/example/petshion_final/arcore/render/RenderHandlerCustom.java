package com.example.petshion_final.arcore.render;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Toast;

import com.example.petshion_final.MainActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class RenderHandlerCustom extends RenderHandler {

    private final static String TAG = RenderHandlerCustom.class.getSimpleName();
    private static float scale = 0.1f;
    public static ArFragment arFragment;

    public RenderHandlerCustom(Context context) {
        super(context);
    }

    /**
     *
     * TODO
     * 좌표 받아오는 거 체크
     * 3d 물체 회전 및 리사이즈
     * 클래스화
     *
     */

    /**
     *
     * TODO
     * controller option
     */

    public static void setArFragment (ArFragment arFragment) {
        RenderHandlerCustom.arFragment = arFragment;
    }


    /**
     *
     * @param modelRenderable
     *
     * modelRenderable is 3D model.
     * this need modelRenderable.build
     *
     * @param pos
     *
     * type ex. glasses, hat
     */
    public static void placeGlassesTest(ModelRenderable modelRenderable, int pos) {
        Vector3 cameraPos = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
        Vector3 cameraForward = arFragment.getArSceneView().getScene().getCamera().getForward();
        Vector3 position = Vector3.add(cameraPos, cameraForward.scaled(1.0f));

        Pose pose = Pose.makeTranslation(position.x, position.y, position.z);
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);

        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        TransformableNode glasses = new TransformableNode(arFragment.getTransformationSystem());
        Quaternion q1 = glasses.getLocalRotation();
        Quaternion q2 = Quaternion.axisAngle(new Vector3(1f, 0f, 0f),  -90);
        glasses.setLocalScale(new Vector3(scale, scale, scale));
        glasses.getScaleController().setMinScale(scale - 0.001f);
        glasses.getScaleController().setMaxScale(scale);
        glasses.setLocalRotation(Quaternion.multiply(q1, q2));
        glasses.setParent(anchorNode);
        glasses.setRenderable(modelRenderable);
        glasses.select();

        // Create an ARCore Anchor at the position.
    }

}

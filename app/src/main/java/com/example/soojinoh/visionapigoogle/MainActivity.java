package com.example.soojinoh.visionapigoogle;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String CLOUD_VISION_API_KEY = "AIzaSyA798GIEtoRBkoNioZBhPe0DV5MZHccb10";
    public static final String FILE_NAME = "temp.jpg";

    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";

    private static final String TAG = MainActivity.class.getSimpleName();


    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;


    private static final int MAX_DIMENSION = 1200;
    private static final int MAX_LABEL_RESULTS = 10;


    private TextView mImageDetails;
    private ImageView mMainImage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        mImageDetails = findViewById(R.id.image_details);
        mMainImage = findViewById(R.id.main_image);

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);

        fab.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder
                    .setMessage(R.string.dialog_select_prompt)
                    .setPositiveButton(R.string.dialog_select_gallery, (dialog, which) -> startGalleryChooser())
                    .setNegativeButton(R.string.dialog_select_camera, (dialog, which) -> startCamera());
            builder.create().show();
        });


    }

    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                    GALLERY_IMAGE_REQUEST); //생성한 Activity로부터 결과를 받아서 호출한 Activity에서 사용하는 방법
            /* Activity의 결과를 받으려면 호출할 때 startActivity() 대신 startActivityForResult() 메소드를 사용해야 합니다.
            새로 호출된 Activity에서는 setResult()를 통해 돌려줄 결과를 저장하고 finish()로 Activity를 종료합니다.
            이후 그 결과는 호출했던 Activity의 onActivityResult() 메소드를 통해 전달되게 됩니다.*/
        }
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(this, CAMERA_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)) {

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);

        }
    }

    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                MAX_DIMENSION);

                callCloudVision(bitmap);
                mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }


    private void callCloudVision(final Bitmap bitmap) {
        // Switch text to loading
        mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, JSONObject> DetectionTask = new DetectionTask(this, prepareAnnotationRequest(bitmap), bitmap);
            DetectionTask.execute();
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    private static class DetectionTask extends AsyncTask<Object, Void, JSONObject> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;
        private Bitmap mBitmap;

        DetectionTask(MainActivity activity, Vision.Images.Annotate annotate, Bitmap bitmap) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
            mBitmap = bitmap;
        }

        @Override
        protected JSONObject doInBackground(Object... params) {

            JSONObject resultJSON = new JSONObject();
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToJSON(response);
//                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }

            try {
                resultJSON.put("msg", "Cloud Vision API request failed. Check logs for details.");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return resultJSON;
        }

        protected void onPostExecute(JSONObject result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.image_details);
                try {
                    imageDetail.setText(result.getString("labels"));
                    imageDetail.append(result.getString("texts"));
//                    imageDetail.append(result.getString("boundary"));
//                    showTextRect(mBitmap, result.getString("boundary"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{

            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);



            annotateImageRequest.setImage(base64EncodedImage);


            // add the features we want :: label_detection
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature labelDetection = new Feature();
                labelDetection.setType("label_DETECTION");
                labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(labelDetection);

                Feature textDetection = new Feature();
                textDetection.setType("TEXT_DETECTION");
                add(textDetection);
            }});
            


            // Add the list of one thing to the request
            add(annotateImageRequest);

        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }

    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder("I found these things:\n\n");


        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();

        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message.append(String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription()));
                message.append("\n");
            }
        } else {
            message.append("nothing");
        }

        List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();

        if (texts != null) {
            for (EntityAnnotation text : texts) {
                message.append(text.getDescription());
                message.append(texts.get(0).getBoundingPoly());
            }
        } else {
            message.append("nothing");
        }

        return message.toString();
    }


    private static JSONObject convertResponseToJSON(BatchAnnotateImagesResponse response) {
        JSONObject message = new JSONObject(response);

        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();


        List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();



        try {
            message.put("labels", labels);
            message.put( "texts", texts);
//            message.put("boundary", textString.append(texts.get(0).getBoundingPoly()));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return message;
    }


//    private void showTextRect(Bitmap bitmap, TextBlock items) {
//
//        Frame frame = new Frame.Builder().SetBitmap(bitmap).Build();
//
//
//        SparseArray items = textRecognizer.Detect(frame);
//
//        List<TextBlock> blocks = new List<TextBlock>() {
//            @Override
//            public int size() {
//                return 0;
//            }
//
//            @Override
//            public boolean isEmpty() {
//                return false;
//            }
//
//            @Override
//            public boolean contains(Object o) {
//                return false;
//            }
//
//            @Override
//            public Iterator<TextBlock> iterator() {
//                return null;
//            }
//
//            @Override
//            public Object[] toArray() {
//                return new Object[0];
//            }
//
//            @Override
//            public <T> T[] toArray(T[] ts) {
//                return null;
//            }
//
//            @Override
//            public boolean add(TextBlock textBlock) {
//                return false;
//            }
//
//            @Override
//            public boolean remove(Object o) {
//                return false;
//            }
//
//            @Override
//            public boolean containsAll(Collection<?> collection) {
//                return false;
//            }
//
//            @Override
//            public boolean addAll(Collection<? extends TextBlock> collection) {
//                return false;
//            }
//
//            @Override
//            public boolean addAll(int i, @NonNull Collection<? extends TextBlock> collection) {
//                return false;
//            }
//
//            @Override
//            public boolean removeAll(Collection<?> collection) {
//                return false;
//            }
//
//            @Override
//            public boolean retainAll(Collection<?> collection) {
//                return false;
//            }
//
//            @Override
//            public void clear() {
//
//            }
//
//            @Override
//            public TextBlock get(int i) {
//                return null;
//            }
//
//            @Override
//            public TextBlock set(int i, TextBlock textBlock) {
//                return null;
//            }
//
//            @Override
//            public void add(int i, TextBlock textBlock) {
//
//            }
//
//            @Override
//            public TextBlock remove(int i) {
//                return null;
//            }
//
//            @Override
//            public int indexOf(Object o) {
//                return 0;
//            }
//
//            @Override
//            public int lastIndexOf(Object o) {
//                return 0;
//            }
//
//            @NonNull
//            @Override
//            public ListIterator<TextBlock> listIterator() {
//                return null;
//            }
//
//            @NonNull
//            @Override
//            public ListIterator<TextBlock> listIterator(int i) {
//                return null;
//            }
//
//            @NonNull
//            @Override
//            public List<TextBlock> subList(int i, int i1) {
//                return null;
//            }
//        };
//
//        TextBlock myItem = null;
//        for (int i = 0; i < items.size(); ++i)
//        {
//            myItem = (TextBlock)items.ValueAt(i);
//
//            //Add All TextBlocks to the `blocks` List
//            blocks.Add(myItem);
//
//        }
//        //END OF DETECTING TEXT
//
//        //The Color of the Rectangle to Draw on top of Text
//        Paint rectPaint = new Paint();
//        rectPaint.setColor(Color.WHITE);
//        rectPaint.setStyle(Paint.Style.STROKE);
//        rectPaint.setStrokeWidth(4.0f);
//
//        //Create the Canvas object,
//        //Which ever way you do image that is ScreenShot for example, you
//        //need the views Height and Width to draw recatngles
//        //because the API detects the position of Text on the View
//        //So Dimesnions are important for Draw method to draw at that Text
//        //Location
//        Bitmap tempBitmap = Bitmap.createBitmap(bitmap.Width, bitmap.Height, Bitmap.Config.Rgb565);
//        Canvas canvas = new Canvas(tempBitmap);
//        canvas.drawBitmap(bitmap, 0, 0, null);
//
//        //Loop through each `Block`
//        foreach (TextBlock textBlock in blocks)
//        {
//            IList<IText> textLines = textBlock.Components;
//
//            //loop Through each `Line`
//            foreach (IText currentLine in textLines)
//            {
//                IList<IText>  words = currentLine.Components;
//
//                //Loop through each `Word`
//                foreach (IText currentword in words)
//                {
//                    //Get the Rectangle/boundingBox of the word
//                    RectF rect = new RectF(currentword.BoundingBox);
//                    rectPaint.Color = Color.Black;
//
//                    //Finally Draw Rectangle/boundingBox around word
//                    canvas.DrawRect(rect, rectPaint);
//
//                    //Set image to the `View`
//                    imgView.SetImageDrawable(new BitmapDrawable(Resources, tempBitmap));
//
//
//                }
//
//            }
//        }
//
//    }


}

package com.company;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.commons.codec.binary.Base64;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;

import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_java;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.opencv.core.MatOfByte;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.Model;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {
    static {
            Loader.load(opencv_java.class);

        }
    public static void main(String[] args) throws IOException {
        String[] TensorCocoClasses=new String[]{
            "background",
            "person",
            "bicycle",
            "car",
            "motorcycle",
            "airplane",
            "bus",
            "train",
            "truck",
            "boat",
            "traffic light",
            "fire hydrant",
            "12",
            "stop sign",
            "parking meter",
            "bench",
            "bird",
            "cat",
            "dog",
            "horse",
            "sheep",
            "cow",
            "elephant",
            "bear",
            "zebra",
            "giraffe",
            "26",
            "backpack",
            "umbrella",
            "29",
            "30",
            "handbag",
            "tie",
            "suitcase",
            "frisbee",
            "skis",
            "snowboard",
            "sports ball",
            "kite",
            "baseball glove",
            "skateboard",
            "surfboard",
            "tennis racket",
            "bottle",
            "45",
            "wine glass",
            "cup",
            "fork",
            "knife",
            "spoon",
            "bowl",
            "banana",
            "apple",
            "sandwich",
            "orange",
            "broccoli",
            "carrot",
            "hot dog",
            "pizza",
            "donut",
            "cake",
            "chair",
            "couch",
            "potted plant",
            "bed",
            "66",
            "dining table",
            "68",
            "69",
            "toilet",
            "71",
            "tv",
            "laptop",
            "mouse",
            "remote",
            "keyboard",
            "cell phone",
            "microwave",
            "oven",
            "toaster",
            "sink",
            "refrigerator",
            "83",
            "book",
            "clock",
            "vase",
            "scissors",
            "teddy bear",
            "hair drier",
            "toothbrush"};
          OpenCVFrameConverter.ToMat converter1 = new OpenCVFrameConverter.ToMat();
        OpenCVFrameConverter.ToOrgOpenCvCoreMat converter2 = new OpenCVFrameConverter.ToOrgOpenCvCoreMat();
        Mat img = org.bytedeco.opencv.global.opencv_imgcodecs.imread("/home/danhyal/notbroken.jpg");
        org.bytedeco.opencv.global.opencv_imgproc.resize(img,img,new Size(1920,1080));

         String host = "localhost";
        int port = 8500;
        // the model's name.
        String modelName = "resnet_openimagesv2";
        String line = "";
        List<String> OpenImageLabels=new ArrayList<>();

        // model's version
        String csvpath="src/com/company/class-descriptions-boxable.csv";
        try (BufferedReader br = new BufferedReader(new FileReader(csvpath))) {
            while ((line = br.readLine()) != null) {
                String[] elements = line.split(",");
                OpenImageLabels.add(elements[1]);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        long modelVersion = 1;
        String openimages="";


            final long startTime = System.currentTimeMillis();
//        org.bytedeco.opencv.global.opencv_imgproc.resize(img, img, new Size(1920, 1080));
//        ImageIO.write(image, "JPEG", out);
        ByteBuffer temp = img.getByteBuffer();
        byte[] arr = new byte[temp.remaining()];
        temp.get(arr);
        opencv_imgcodecs.imencode(".jpg", img, arr);

        // create a channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().maxInboundMessageSize(402180070).build();

        PredictionServiceGrpc.PredictionServiceBlockingStub stub = PredictionServiceGrpc.newBlockingStub(channel);

        // create a modelspec
        Model.ModelSpec.Builder modelSpecBuilder = Model.ModelSpec.newBuilder();
        modelSpecBuilder.setName(modelName);
        modelSpecBuilder.setVersion(Int64Value.of(modelVersion));
        modelSpecBuilder.setSignatureName("serving_default");

        Predict.PredictRequest.Builder builder = Predict.PredictRequest.newBuilder();
        builder.setModelSpec(modelSpecBuilder);

        // create the TensorProto and request
        TensorProto.Builder tensorProtoBuilder = TensorProto.newBuilder();
        tensorProtoBuilder.setDtype(DataType.DT_STRING);
        TensorShapeProto.Builder tensorShapeBuilder = TensorShapeProto.newBuilder();
        tensorShapeBuilder.addDim(TensorShapeProto.Dim.newBuilder().setSize(1));
        tensorProtoBuilder.setTensorShape(tensorShapeBuilder.build());
        tensorProtoBuilder.addStringVal(ByteString.copyFrom(arr));
        TensorProto tp = tensorProtoBuilder.build();

        builder.putInputs("inputs", tp);

        Predict.PredictRequest request = builder.build();
        Predict.PredictResponse response = stub.predict(request);

        Map<String, TensorProto> outputmap = response.getOutputsMap();
        int num_detections = (int) outputmap.get("num_detections").getFloatVal(0);
        List<Float> detection_classes = outputmap.get("detection_classes").getFloatValList();
        List<Float> detection_boxes_big = outputmap.get("detection_boxes").getFloatValList();
        List<List<Float>> detection_boxes = Lists.partition(detection_boxes_big, 4);
        List<Float> detection_scores=outputmap.get("detection_scores").getFloatValList();
//        System.out.println(detection_classes);
//        System.out.println(num_detections);
        for (int j=0;j<num_detections;j+=1){
            double confidance=detection_scores.get(j);

            if (confidance>0.7){
                int top= (int) (detection_boxes.get(j).get(0)*img.rows());
                int left=(int)(detection_boxes.get(j).get(1)*img.cols());
                int bottom=(int)(detection_boxes.get(j).get(2)*img.rows());
                int right=(int)(detection_boxes.get(j).get(3)*img.cols());
                org.bytedeco.opencv.global.opencv_imgproc.rectangle(img,new Point(left,top),new Point(right,bottom), Scalar.GREEN);
//                org.bytedeco.opencv.global.opencv_imgproc.putText(img,TensorCocoClasses[detection_classes.get(j).intValue()],new Point(left,top),1,1,Scalar.RED);
                org.bytedeco.opencv.global.opencv_imgproc.putText(img, OpenImageLabels.get(detection_classes.get(j).intValue()-1),new Point(left,top),1,1,Scalar.RED);


            }
        }
        channel.shutdown();
        org.bytedeco.opencv.global.opencv_imgcodecs.imwrite("/home/danhyal/processed.jpg",img);
        final long endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime));







    }
   
}

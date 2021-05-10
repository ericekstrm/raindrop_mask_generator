package raindrop.mask.generator;

import java.io.File;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;


public class RaindropMaskGenerator extends Application {

    public static final int SIZE = 512;

    HBox bottomBar = new HBox();
    Slider sphereRadiusSlider;
            
    Canvas canvas = new Canvas(SIZE, SIZE);
    Canvas sideView = new Canvas(SIZE, SIZE);
    Canvas outputImage = new Canvas(SIZE, SIZE);

    Image image;
    
    Point3D sphereCenter = new Point3D(256, 256, 200);
    float sphereRadiusDefault = 200;
    Point3D incidentVector = new Point3D(0, 0, 1);
    double imageDepth = 500;
    
    double n1 = 1;
    double n2 = 1.333;
    
    @Override
    public void start(Stage primaryStage) {

        image = new Image("https://i.guim.co.uk/img/media/8a13052d4db7dcd508af948e5db7b04598e03190/0_294_5616_3370/master/5616.jpg?width=1200&height=1200&quality=85&auto=format&fit=crop&s=bcaa4eed2c1e6dab61c41a61e41433d9");

        bottomBar.setStyle("-fx-padding: 2;" + "-fx-border-style: solid inside;"
                + "-fx-border-width: 2 0 0 0;");
        
        Button exportButton = new Button("Export to png");
        exportButton.setOnAction((event) -> {
            System.out.println("WEriting to file!");
            WritableImage wim = new WritableImage(SIZE, SIZE);
            outputImage.snapshot(null, wim);
            File file = new File("CanvasImage.png");
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(wim, null), "png", file);
            } catch (Exception s) {
                System.out.println("could not save image");
            }
        });
        bottomBar.getChildren().add(exportButton);
        
        sphereRadiusSlider = new Slider(10, SIZE / 2, sphereRadiusDefault);
        sphereRadiusSlider.valueProperty().addListener((observable, oldValue, newValue) -> {

            render();
        });
        bottomBar.getChildren().add(new Label("SphereRadius:"));
        bottomBar.getChildren().add(sphereRadiusSlider);

        StackPane root = new StackPane();
        root.getChildren().add(new VBox(new HBox(outputImage, canvas, sideView), bottomBar));

        Scene scene = new Scene(root);

        primaryStage.setTitle("Raindrop mask generator");
        primaryStage.setScene(scene);
        primaryStage.show();

        render();
    }
    
    public void render() {
        
        //clear screen
        sideView.getGraphicsContext2D().setFill(Color.WHITE);
        sideView.getGraphicsContext2D().fillRect(0, 0, SIZE, SIZE);
        sideView.getGraphicsContext2D().setFill(Color.BLACK);
        outputImage.getGraphicsContext2D().setFill(Color.WHITE);
        outputImage.getGraphicsContext2D().fillRect(0, 0, SIZE, SIZE);
        
        PixelWriter main_pw = canvas.getGraphicsContext2D().getPixelWriter();
        PixelWriter output_pw = outputImage.getGraphicsContext2D().getPixelWriter();
        
        int sphereRadius = (int) sphereRadiusSlider.getValue();
        
        //draw cat image
        canvas.getGraphicsContext2D().drawImage(image, 0, 0);
        
        //draw raindrop in sideview
        sideView.getGraphicsContext2D().strokeOval(sphereCenter.getZ() - sphereRadius, sphereCenter.getY() - sphereRadius, sphereRadius * 2, sphereRadius * 2);

        //draw image plane
        sideView.getGraphicsContext2D().strokeLine(imageDepth, 0, imageDepth, SIZE);

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                Point2D dist = new Point2D(sphereCenter.getX() - x, sphereCenter.getY() - y);
                if (dist.magnitude() < sphereRadius) {

                    Point3D spherePoint = getSpherePoint(x, y, sphereRadius, sphereCenter);
                    Point3D internalDir = getRefraction(incidentVector, getNormal(sphereCenter, spherePoint), n1 / n2);
                    Point3D secondSpherePoint = sphereExitPoint(spherePoint, internalDir, sphereCenter, sphereRadius);
                    Point3D refractionVector = getRefraction(internalDir, getNormal(secondSpherePoint, sphereCenter), n2 / n1);
                    Point3D imagePoint = imageIntersect(secondSpherePoint, refractionVector, imageDepth);

                    if (imagePoint.getX() > SIZE || imagePoint.getX() < 0 || imagePoint.getY() > SIZE || imagePoint.getY() < 0) {
                        main_pw.setColor(x, y, Color.GRAY);
                    } else {
                        main_pw.setColor(x, y, image.getPixelReader().getColor((int) imagePoint.getX(), (int) imagePoint.getY()));
                    }

                    //draw to side view
                    if (x == sphereCenter.getX() && y % 2 == 0) {
                        Point3D startPoint = new Point3D(x, y, 0);

                        drawVector(startPoint, spherePoint.subtract(startPoint));

                        //draw internal vector
                        drawVector(spherePoint, secondSpherePoint.subtract(spherePoint));

                        //draw refraction vector
                        drawVector(secondSpherePoint, imagePoint.subtract(secondSpherePoint));
                    }

                    //draw to output image
                    Point3D outColor = refractionVector.normalize().multiply(0.5).add(0.5, 0.5, 0.5);
                    output_pw.setColor(x, y, new Color((float) outColor.getX(), (float) outColor.getY(), 0, 1));
                }
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    public static Point3D getSpherePoint(double x, double y, double sphereRadius, Point3D sphereCenter) {

        double z = -Math.sqrt(sphereRadius * sphereRadius - Math.pow(x - sphereCenter.getX(), 2) - Math.pow(y - sphereCenter.getY(), 2)) + sphereCenter.getZ();

        return new Point3D(x, y, z);
    }

    public static Point3D getNormal(Point3D sphereCenter, Point3D spherePoint) {
        Point3D normal = spherePoint.subtract(sphereCenter);
        return normal.normalize();
    }

    public static Point3D getRefraction(Point3D incident, Point3D normal, double mu) {
        incident = incident.normalize();
        normal = normal.normalize();

        double c = -normal.dotProduct(incident);
        Point3D refractionVector = incident.multiply(mu).add(normal.multiply(c * mu - Math.sqrt(1 - mu * mu * (1 - c * c))));
        return refractionVector.normalize();
    }

    public static Point3D sphereExitPoint(Point3D entrancePoint, Point3D dir, Point3D sphereCenter, double sphereRadius) {

        dir = dir.normalize();

        double t = dir.dotProduct(sphereCenter.subtract(entrancePoint));

        return entrancePoint.add(dir.multiply(2 * t));
    }

    public static Point3D imageIntersect(Point3D startPoint, Point3D dir, double imageZ) {

        double t = (imageZ - startPoint.getZ()) / dir.getZ();

        return startPoint.add(dir.multiply(t));

    }

    public void drawVector(Point3D start, Point3D vector) {

        GraphicsContext gc = sideView.getGraphicsContext2D();

        gc.fillOval(start.getZ() - 2, start.getY() - 2, 4, 4);

        gc.strokeLine(start.getZ(), start.getY(), start.getZ() + vector.getZ(), start.getY() + vector.getY());

    }
    
}

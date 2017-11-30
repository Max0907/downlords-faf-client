package com.wilutions.jiraddin;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class PasteImageFromClipboard extends Application {

  ImageView imageView = new ImageView();
  Button bnPaste = new Button("Paste");

  public static void main(String[] args) {
    Application.launch(args);
  }

  private static javafx.scene.image.Image awtImageToFX(java.awt.Image image) throws Exception {
    if (!(image instanceof RenderedImage)) {
      BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null),
          BufferedImage.TYPE_INT_ARGB);
      Graphics g = bufferedImage.createGraphics();
      g.drawImage(image, 0, 0, null);
      g.dispose();

      image = bufferedImage;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write((RenderedImage) image, "png", out);
    out.flush();
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    return new javafx.scene.image.Image(in);
  }

  @Override
  public void start(Stage stage) throws Exception {

    bnPaste.setOnAction(new EventHandler<ActionEvent>() {
      public void handle(ActionEvent event) {
        try {
          java.awt.Image image = getImageFromClipboard();
          if (image != null) {
            javafx.scene.image.Image fimage = awtImageToFX(image);
            imageView.setImage(fimage);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });

    VBox vbox = new VBox();
    vbox.getChildren().addAll(bnPaste, imageView);
    Scene scene = new Scene(vbox);
    stage.setScene(scene);
    stage.setWidth(400);
    stage.setHeight(400);
    stage.show();
  }

  private java.awt.Image getImageFromClipboard() {
    Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
    if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
      try {
        return (java.awt.Image) transferable.getTransferData(DataFlavor.imageFlavor);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }

}

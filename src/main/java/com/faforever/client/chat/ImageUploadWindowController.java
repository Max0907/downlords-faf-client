package com.faforever.client.chat;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.uploader.ImageUploadService;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@Slf4j
public class ImageUploadWindowController implements Controller<Node> {
  protected final ImageUploadService imageUploadService;
  public Pane imageUploadRoot;
  public ImageView imagePreview;
  private Consumer<CompletableFuture<URL>> callback;

  public ImageUploadWindowController(ImageUploadService imageUploadService) {
    this.imageUploadService = imageUploadService;
  }

  @Override
  public Region getRoot() {
    return imageUploadRoot;
  }

  public void setCallback(Consumer<CompletableFuture<URL>> callback) {
    this.callback = callback;
  }

  public void setImage(Image image) {
    imagePreview.setImage(image);
    imagePreview.setPreserveRatio(true);
  }

  public void onCancelButtonClicked() {
    getRoot().getScene().getWindow().hide();
  }

  public void onUploadButtonClicked() {
    callback.accept(imageUploadService.uploadImageInBackground(JavaFxUtil.getImageFromClipboard()));
    onCancelButtonClicked();
  }
}

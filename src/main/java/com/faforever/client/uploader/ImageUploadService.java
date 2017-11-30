package com.faforever.client.uploader;

import javafx.scene.image.Image;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public interface ImageUploadService {

  CompletableFuture<URL> uploadImageInBackground(Image image);
}

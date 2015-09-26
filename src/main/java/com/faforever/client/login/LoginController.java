package com.faforever.client.login;

import com.faforever.client.fx.SceneFactory;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.MainController;
import com.faforever.client.preferences.LoginPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.JavaFxUtil;
import com.google.common.base.Strings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.invoke.MethodHandles;

import static com.faforever.client.fx.WindowDecorator.WindowButtonType.CLOSE;
import static com.faforever.client.fx.WindowDecorator.WindowButtonType.MINIMIZE;
import static com.google.common.base.Strings.isNullOrEmpty;

public class LoginController {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @FXML
  Pane loginFormPane;

  @FXML
  Pane loginProgressPane;

  @FXML
  CheckBox autoLoginCheckBox;

  @FXML
  TextField usernameInput;

  @FXML
  TextField passwordInput;

  @FXML
  Button loginButton;

  @FXML
  Region loginRoot;

  @Autowired
  MainController mainController;

  @Autowired
  I18n i18n;

  @Autowired
  UserService userService;

  @Autowired
  PreferencesService preferencesService;

  @Autowired
  SceneFactory sceneFactory;

  private Stage stage;

  @FXML
  private void initialize() {
    loginProgressPane.setVisible(false);
  }

  public void display(Stage stage) {
    this.stage = stage;

    sceneFactory.createScene(stage, loginRoot, false, MINIMIZE, CLOSE);

    stage.setTitle(i18n.get("login.title"));
    stage.setResizable(false);

    LoginPrefs loginPrefs = preferencesService.getPreferences().getLogin();
    String username = loginPrefs.getUsername();
    String password = loginPrefs.getPassword();
    boolean isAutoLogin = loginPrefs.getAutoLogin();

    // Fill the form even if autoLogin is true, since user may cancel the login
    usernameInput.setText(Strings.nullToEmpty(username));
    autoLoginCheckBox.setSelected(isAutoLogin);

    stage.show();
    JavaFxUtil.centerOnScreen(stage);

    if (loginPrefs.getAutoLogin() && !isNullOrEmpty(username) && !isNullOrEmpty(password)) {
      login(username, password, true);
    } else if (isNullOrEmpty(username)) {
      usernameInput.requestFocus();
    } else {
      passwordInput.requestFocus();
    }
  }

  private void login(String username, String password, boolean autoLogin) {
    onLoginProgress();

    userService.login(username, password, autoLogin)
        .thenAccept(aVoid -> onLoginSucceeded())
        .exceptionally(throwable -> {
          onLoginFailed(throwable);
          return null;
        });
  }

  private void onLoginProgress() {
    setShowLoginProgress(true);
  }

  private void onLoginSucceeded() {
    mainController.display(stage);
  }

  private void onLoginFailed(Throwable e) {
    logger.warn("Login failed", e);

    Dialog<Void> loginFailedDialog = new Dialog<>();
    loginFailedDialog.setTitle(i18n.get("login.failed.title"));
    loginFailedDialog.setContentText(i18n.get("login.failed.message"));
    loginFailedDialog.show();

    loginFormPane.setVisible(true);
    loginProgressPane.setVisible(false);
    loginButton.setDisable(false);
  }

  private void setShowLoginProgress(boolean b) {
    loginFormPane.setVisible(!b);
    loginProgressPane.setVisible(b);
    loginButton.setDisable(b);
  }

  @FXML
  void loginButtonClicked() {
    String username = usernameInput.getText();
    String password = passwordInput.getText();

    password = DigestUtils.sha256Hex(password);

    boolean autoLogin = autoLoginCheckBox.isSelected();

    login(username, password, autoLogin);
  }

  @FXML
  void onCloseButtonClicked() {
    stage.close();
  }

  @FXML
  void onMinimizeButtonClicked() {
    stage.setIconified(true);
  }

  @FXML
  public void onCancelLoginButtonClicked() {
    userService.cancelLogin();
    setShowLoginProgress(false);
  }
}
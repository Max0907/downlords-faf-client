package com.faforever.client.game;

import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.faforever.client.vault.replay.WatchButtonController;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.scene.layout.Pane;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CustomGamesControllerTest extends AbstractPlainJavaFxTest {

  private CustomGamesController instance;
  @Mock
  private GameService gameService;
  @Mock
  private PreferencesService preferencesService;
  @Mock
  private Preferences preferences;
  @Mock
  private UiService uiService;
  @Mock
  private GamesTableController gamesTableController;
  @Mock
  private EventBus eventBus;
  @Mock
  private GameDetailController gameDetailController;
  @Mock
  private WatchButtonController watchButtonController;
  @Mock
  private I18n i18n;
  @Mock
  private GamesTilesContainerController gamesTilesContainerController;

  private ObservableList games;

  @Before
  public void setUp() throws Exception {
    instance = new CustomGamesController(uiService, gameService, preferencesService, eventBus, i18n);

    games = FXCollections.observableArrayList();

    when(gameService.getGames()).thenReturn(games);
    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferences.getShowModdedGamesProperty()).thenReturn(new SimpleBooleanProperty(true));
    when(preferences.getShowPasswordProtectedGamesProperty()).thenReturn(new SimpleBooleanProperty(true));
    when(preferences.getGamesViewMode()).thenReturn("tableButton");
    when(uiService.loadFxml("theme/play/games_table.fxml")).thenReturn(gamesTableController);
    when(uiService.loadFxml("theme/play/games_tiles_container.fxml")).thenReturn(gamesTilesContainerController);
    when(gamesTilesContainerController.getRoot()).thenReturn(new Pane());
    when(gamesTableController.getRoot()).thenReturn(new Pane());
    when(gamesTableController.selectedGameProperty()).thenReturn(new SimpleObjectProperty<>());
    when(gamesTilesContainerController.selectedGameProperty()).thenReturn(new SimpleObjectProperty<>());

    loadFxml("theme/play/custom_games.fxml", clazz -> {
      if (clazz == GameDetailController.class) {
        return gameDetailController;
      }
      if (clazz == WatchButtonController.class) {
        return watchButtonController;
      }
      return instance;
    });
  }

  @Test
  public void testSetSelectedGameShowsDetailPane() throws Exception {
    assertFalse(instance.gameDetailPane.isVisible());
    instance.setSelectedGame(GameBuilder.create().defaultValues().get());
    assertTrue(instance.gameDetailPane.isVisible());
  }

  @Test
  public void testSetSelectedGameNullHidesDetailPane() throws Exception {
    instance.setSelectedGame(GameBuilder.create().defaultValues().get());
    assertTrue(instance.gameDetailPane.isVisible());
    instance.setSelectedGame(null);
    assertFalse(instance.gameDetailPane.isVisible());
  }

  @Test
  public void testShowModdedGames() throws Exception {
    ObservableList<Game> temp = FXCollections.observableArrayList();
    Game game = GameBuilder.create().defaultValues().get();
    ObservableMap<String, String> simMods = FXCollections.observableHashMap();
    simMods.put("123-456-789", "Fake mod name");
    game.setSimMods(simMods);
    temp.add(game);
    instance.filteredItems = new FilteredList<>(temp, s -> true);
    instance.showModdedGamesCheckBox.setSelected(true);
    instance.onShowModdedGames(new ActionEvent(instance.showModdedGamesCheckBox, null));

    assertTrue(instance.filteredItems.size()>0);

    instance.showModdedGamesCheckBox.setSelected(false);
    instance.onShowModdedGames(new ActionEvent(instance.showModdedGamesCheckBox, null));
    assertTrue(instance.filteredItems.isEmpty());
  }

  @Test
  public void testShowPasswordProtectedGames() throws Exception {
    ObservableList<Game> temp = FXCollections.observableArrayList();
    Game game = GameBuilder.create().defaultValues().get();
    game.passwordProtectedProperty().set(true);
    temp.add(game);
    instance.filteredItems = new FilteredList<>(temp, s -> true);
    instance.showPasswordProtectedGamesCheckBox.setSelected(true);
    instance.onShowModdedGames(new ActionEvent(instance.showPasswordProtectedGamesCheckBox, null));

    assertTrue(instance.filteredItems.size()>0);

    instance.showPasswordProtectedGamesCheckBox.setSelected(false);
    instance.onShowModdedGames(new ActionEvent(instance.showPasswordProtectedGamesCheckBox, null));
    assertTrue(instance.filteredItems.isEmpty());
  }

  @Test
  public void testTiles() throws Exception {
    instance.tilesButton.getOnAction().handle(new ActionEvent());
    WaitForAsyncUtils.waitForFxEvents();
    verify(gamesTilesContainerController).createTiledFlowPane(games, instance.chooseSortingTypeChoiceBox);
  }
}

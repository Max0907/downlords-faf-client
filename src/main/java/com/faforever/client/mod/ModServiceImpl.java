package com.faforever.client.mod;

import com.faforever.client.legacy.LobbyServerAccessor;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.TaskService;
import com.faforever.client.util.ConcurrentUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

public class ModServiceImpl implements ModService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile("\"(.*?)\"");
  private static final Pattern ACTIVE_MODS_PATTERN = Pattern.compile("active_mods\\s*=\\s*\\{.*?}", Pattern.DOTALL);
  private static final Pattern ACTIVE_MOD_PATTERN = Pattern.compile("\\['(.*?)']\\s*=\\s*(true|false)", Pattern.DOTALL);

  @Autowired
  LobbyServerAccessor lobbyServerAccessor;

  @Autowired
  PreferencesService preferencesService;

  @Autowired
  TaskService taskService;

  @Autowired
  ApplicationContext applicationContext;

  private Path modsDirectory;
  private Map<Path, ModInfoBean> pathToMod;
  private ObservableList<ModInfoBean> installedMods;
  private ObservableSet<ModInfoBean> availableMods;
  private ObservableSet<ModInfoBean> readOnlyAvailableMods;

  public ModServiceImpl() {
    pathToMod = new HashMap<>();
    installedMods = FXCollections.observableArrayList();
    availableMods = FXCollections.observableSet();
    readOnlyAvailableMods = FXCollections.unmodifiableObservableSet(availableMods);
  }

  @Override
  public ObservableSet<ModInfoBean> getAvailableMods() {
    return readOnlyAvailableMods;
  }

  @Override
  public void loadInstalledMods() {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(modsDirectory)) {
      for (Path path : directoryStream) {
        addMod(path);
      }
    } catch (IOException e) {
      logger.warn("Mods could not be read from: " + modsDirectory, e);
    }
  }

  @Override
  public ObservableList<ModInfoBean> getInstalledMods() throws IOException {
    return installedMods;
  }

  @Override
  public CompletableFuture<Void> downloadAndInstallMod(String modPath) {
    DownloadModTask task = applicationContext.getBean(DownloadModTask.class);
    task.setModPath(modPath);
    return taskService.submitTask(task)
        .thenAccept(aVoid -> loadInstalledMods());
  }

  @Override
  public Set<String> getInstalledModUids() throws IOException {
    return getInstalledMods().stream()
        .map(ModInfoBean::getUid)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<String> getInstalledUiModsUids() throws IOException {
    return getInstalledMods().stream()
        .filter(ModInfoBean::getUiOnly)
        .map(ModInfoBean::getUid)
        .collect(Collectors.toSet());
  }

  @Override
  public void enableSimMods(Set<String> simMods) throws IOException {
    Map<String, Boolean> modStates = readModStates();

    Set<String> installedUiMods = getInstalledUiModsUids();

    for (Map.Entry<String, Boolean> entry : modStates.entrySet()) {
      String uid = entry.getKey();

      if (!installedUiMods.contains(uid)) {
        // Only disable it if it's a sim mod; because it has not been selected
        entry.setValue(false);
      }
    }
    for (String simModUid : simMods) {
      modStates.put(simModUid, true);
    }

    writeModStates(modStates);
  }

  @Override
  public void requestMods() {
    lobbyServerAccessor.requestMods();
  }

  private Map<String, Boolean> readModStates() throws IOException {
    Path preferencesFile = preferencesService.getPreferences().getForgedAlliance().getPreferencesFile();
    Map<String, Boolean> mods = new HashMap<>();

    String preferencesContent = new String(Files.readAllBytes(preferencesFile), US_ASCII);
    Matcher matcher = ACTIVE_MODS_PATTERN.matcher(preferencesContent);
    if (matcher.find()) {
      Matcher activeModMatcher = ACTIVE_MOD_PATTERN.matcher(matcher.group(0));
      while (activeModMatcher.find()) {
        String modUid = activeModMatcher.group(1);
        boolean enabled = Boolean.parseBoolean(activeModMatcher.group(2));

        mods.put(modUid, enabled);
      }
    }

    return mods;
  }

  private void writeModStates(Map<String, Boolean> modStates) throws IOException {
    Path preferencesFile = preferencesService.getPreferences().getForgedAlliance().getPreferencesFile();
    String preferencesContent = new String(Files.readAllBytes(preferencesFile), US_ASCII);

    String currentActiveModsContent = null;
    Matcher matcher = ACTIVE_MODS_PATTERN.matcher(preferencesContent);
    if (matcher.find()) {
      currentActiveModsContent = matcher.group(0);
    }

    StringBuilder newActiveModsContentBuilder = new StringBuilder("active_mods = {");

    Iterator<Map.Entry<String, Boolean>> iterator = modStates.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Boolean> entry = iterator.next();
      if (!entry.getValue()) {
        continue;
      }

      newActiveModsContentBuilder.append("\n    ['");
      newActiveModsContentBuilder.append(entry.getKey());
      newActiveModsContentBuilder.append("'] = true");
      if (iterator.hasNext()) {
        newActiveModsContentBuilder.append(",");
      }
    }
    newActiveModsContentBuilder.append("\n}");

    if (currentActiveModsContent != null) {
      preferencesContent = preferencesContent.replace(currentActiveModsContent, newActiveModsContentBuilder);
    } else {
      preferencesContent += newActiveModsContentBuilder.toString();
    }

    Files.write(preferencesFile, preferencesContent.getBytes(US_ASCII));
  }

  private void addMod(Path path) throws IOException {
    ModInfoBean modInfoBean = extractModInfo(path);
    if (modInfoBean == null) {
      return;
    }
    pathToMod.put(path, modInfoBean);
    if (!installedMods.contains(modInfoBean)) {
      installedMods.add(modInfoBean);
    }
  }

  private ModInfoBean extractModInfo(Path path) throws IOException {
    ModInfoBean modInfoBean = new ModInfoBean();

    Path modInfoLua = path.resolve("mod_info.lua");
    if (Files.notExists(modInfoLua)) {
      return null;
    }

    try (InputStream inputStream = Files.newInputStream(modInfoLua)) {
      Properties properties = new Properties();
      properties.load(inputStream);

      modInfoBean.setUid(stripQuotes(properties.getProperty("uid")));
      modInfoBean.setName(stripQuotes(properties.getProperty("name")));
      modInfoBean.setDescription(stripQuotes(properties.getProperty("description")));
      modInfoBean.setAuthor(stripQuotes(properties.getProperty("author")));
      modInfoBean.setVersion(stripQuotes(properties.getProperty("version")));
      modInfoBean.setSelectable(Boolean.parseBoolean(stripQuotes(properties.getProperty("selectable"))));
      modInfoBean.setUiOnly(Boolean.parseBoolean(stripQuotes(properties.getProperty("ui_only"))));
      modInfoBean.setImagePath(extractIconPath(path, properties));
    }

    return modInfoBean;
  }

  private static String stripQuotes(String string) {
    if (string == null) {
      return null;
    }

    Matcher matcher = QUOTED_TEXT_PATTERN.matcher(string);
    if (matcher.find()) {
      return matcher.group(1);
    }

    return string;
  }

  private static Path extractIconPath(Path path, Properties properties) {
    String icon = properties.getProperty("icon");
    if (icon == null) {
      return null;
    }

    icon = stripQuotes(icon);

    if (StringUtils.isEmpty(icon)) {
      return null;
    }

    if (icon.startsWith("/")) {
      icon = icon.substring(1);
    }

    Path iconPath = Paths.get(icon);
    // mods/BlackOpsUnleashed/icons/yoda_icon.bmp -> icons/yoda_icon.bmp
    iconPath = iconPath.subpath(2, iconPath.getNameCount());

    return path.resolve(iconPath);
  }

  @PostConstruct
  void postConstruct() throws IOException, InterruptedException {
    lobbyServerAccessor.setOnModInfoListener(modInfo -> availableMods.add(ModInfoBean.fromModInfo(modInfo)));
    modsDirectory = preferencesService.getPreferences().getForgedAlliance().getModsDirectory();
    startDirectoryWatcher(modsDirectory);
    loadInstalledMods();
  }

  private void startDirectoryWatcher(Path modsDirectory) throws IOException, InterruptedException {
    ConcurrentUtil.executeInBackground(new Task<Void>() {
      @Override
      protected Void call() throws Exception {
        WatchService watcher = modsDirectory.getFileSystem().newWatchService();
        modsDirectory.register(watcher, ENTRY_DELETE);

        while (true) {
          WatchKey key = watcher.take();
          for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == ENTRY_DELETE) {
              removeMod(modsDirectory.resolve((Path) event.context()));
            }
          }
          key.reset();
        }
      }
    });
  }

  private void removeMod(Path path) throws IOException {
    installedMods.remove(pathToMod.remove(path));
  }
}
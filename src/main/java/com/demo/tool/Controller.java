package com.demo.tool;

import com.demo.tool.resource.ResourcePaneController;
import com.demo.tool.resource.ResourcePartitionController;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;

import static com.demo.tool.Utils.judgeFloat;
import static com.demo.tool.Utils.judgeInteger;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Controller {

    @FXML private ComboBox<String> methodComboBox;
    @FXML private ListView<String> selectedMethodsList;
    private List<Map<String, Double>> result; // 存储所有结果数据
    private int currentIndex = 0; // 当前图表结果的索引

    public static Logger log = LogManager.getLogger();

    @FXML
    public Button batchTest;
    @FXML
    public BarChart<String, Number> barChart1;

    @FXML
    public BarChart<String, Number> barChart2;

    @FXML
    public BarChart<String, Number> barChart3;

    @FXML
    public Button config;
    @FXML
    private TextField coreNum;
    @FXML
    private TextField taskNum;
    @FXML
    private TextField utility;
    @FXML
    private TextField rangeT1;
    @FXML
    private TextField rangeT2;
    @FXML
    private TextField resourceNum;
    @FXML
    private TextField rsf;
    @FXML
    private TextField maxAccess;
    @FXML
    private TextField rangeCSL1;
    @FXML
    private TextField rangeCSL2;
    @FXML
    private ComboBox<String> allocation;
    @FXML
    private ComboBox<String> RTM;
    @FXML
    private Region loading;
    @FXML
    private Region generating;
    @FXML
    private ComboBox<String> priority;
    @FXML
    private TextField sysNum_batch;
    @FXML
    private TextField coreNum_batch;
    @FXML
    private TextField taskNum_batch;
    @FXML
    private TextField utility_batch;
    @FXML
    private TextField rangeT1_batch;
    @FXML
    private TextField rangeT2_batch;
    @FXML
    private TextField resourceNum_batch;
    @FXML
    private TextField rsf_batch;
    @FXML
    private TextField maxAccess_batch;
    @FXML
    private TextField rangeCSL1_batch;
    @FXML
    private TextField rangeCSL2_batch;
    @FXML
    private ComboBox<String> allocation_batch;
    @FXML
    private ComboBox<String> priority_batch;
    @FXML
    private Label scheduleNO;
    @FXML
    private Label scheduleYES;
    @FXML
    private Label startLabel;
    @FXML
    private Label generateLabel;
    @FXML
    private ComboBox<String> systemMode;
    @FXML
    private TableView<Object[]> table1;
    @FXML
    private TableView<Object[]> table2;
    @FXML
    private VBox page11;
    @FXML
    private VBox page12;
    @FXML
    private HBox page2;
    @FXML
    private VBox page3;
    @FXML
    private StackPane page1btn;
    @FXML
    private StackPane page2btn;
    @FXML
    private StackPane page3btn;
    @FXML
    private Tooltip tipsForU;
    @FXML
    private ScrollPane pPartition;
    @FXML
    private ScrollPane pResource;
    @FXML
    private TextField text_FIFONP_LOCK;
    @FXML
    private TextField text_FIFONP_UNLOCK;
    @FXML
    private TextField text_FIFOP_CANCEL;
    @FXML
    private TextField text_FIFOP_LOCK;
    @FXML
    private TextField text_FIFOP_UNLOCK;
    @FXML
    private TextField text_Mrsp_LOCK;
    @FXML
    private TextField text_Mrsp_UNLOCK;
    @FXML
    private TextField text_Mrsp_PREEMPTION_AND_MIGRATION;
    @FXML
    private TextField text_FULL_CONTEXT_SWITCH2;
    @FXML
    private Factors factors;


    private Factors batchTestFactors;
    private Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> pair;
    private Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> batchTestPair;
    private boolean generateDone = false;
    private int taskShowing = -1;
    private boolean taskClickedFactoryAwake = false;
    private int taskClickCount = 1;
    private int currentShowingResource = -1;
    private int resourceClickCount = 0;

    @FXML
    private Pagination pagination;



    @FXML
    private TextArea jsonTextArea;

    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            loadJsonFile(selectedFile);
        } else {
            showAlert("File selection cancelled.");
        }
    }

    private void loadJsonFile(File file) {
        ObjectMapper mapper = new ObjectMapper();
        initAnalysis();
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            jsonTextArea.setText(content);
            factors.TOTAL_PARTITIONS = mapper.readTree(content).path("system").path("core_count").asInt();
            System.out.println(factors.TOTAL_PARTITIONS);
            pair = new Analysis().processJsonSystem(content);

            ArrayList<ArrayList<SporadicTask>> tasks = pair.getFirst();
            for (int i = 0; i < tasks.size(); i++){
                for (int j = 0; j<tasks.get(i).size(); j++){
                    System.out.println(tasks.get(i).get(j).getInfo());
                }
            }
            for (Resource res: pair.getSecond()){
                System.out.println(res.id + " " + res.csl_low +" "+res.csl_high);
            }

            /* set the start icon inaccessible */
            generateLabel.setVisible(false);
            generating.setVisible(true);



            generateTasksTable(pair.getFirst());
//        fillTable3(pair.getSecond());
            generateResource(pair.getSecond());
            generatePartition();

            generateDone = true;

            /* set the start icon accessible */
            generateLabel.setVisible(true);
            generating.setVisible(false);

            // show data on the screen
//        onWakeUpTaskClicked();
//        taskClicked();
            generateResourceAccessButton();
            resourceClicked();

        } catch (IOException e) {
            showAlert("Error reading file: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    /**
     * 判断参数是否合法并传递为double，不合法为Double.MIN_VALUE
     *
     * @param textField
     * @return
     * @throws NumberFormatException
     */
    private static double parseDouble(TextField textField) throws NumberFormatException {
        double value = Double.MIN_VALUE;
        try {
            value = Double.parseDouble(textField.getText());
        } catch (NumberFormatException e) {
            //textField.setText("illegal value");
        }
        return value;
    }

    void batchTesting() {

    }

    void test0() {
        page1btn.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                coreNum.setText("16");
                taskNum.setText("80");
                utility.setText("8");
                rangeT1.setText("1");
                rangeT2.setText("100");
                resourceNum.setText("16");
                rsf.setText("0.3");
                maxAccess.setText("40");
                rangeCSL1.setText("1");
                rangeCSL2.setText("100");
                allocation.setValue("WF");
            } else {
                onChangeToPage1();
            }
        });
    }

    @FXML
    void initialize() {
        log.info("Controller initialization started");
        System.out.println(BootstrapFX.bootstrapFXStylesheet());
        initComBox();
        initAnalysis();
        initPageBtnStyle();
        initConfig();
        tipsForU.setShowDelay(javafx.util.Duration.millis(100));
        tipsForU.setHideDelay(javafx.util.Duration.millis(100));

        // 监听排序事件，当排序发生变化时更新行样式
        table2.getSortOrder().addListener((ListChangeListener<? super TableColumn<Object[], ?>>) change -> {
            table2.refresh(); // 重新渲染表格以更新行样式
        });
        resultTableChangeColorWithSort();

        utility.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                Bounds screenBounds = page11.localToScreen(page11.getBoundsInLocal());
                tipsForU.show(utility, screenBounds.getCenterX() + utility.getWidth() * 0.4, screenBounds.getCenterY() - utility.getParent().getBoundsInLocal().getHeight() * 2.75);
            } else {
                tipsForU.hide();
            }
        });

        //test
        test0();



        log.info("Controller initialization completed");
    }

    void initPageBtnStyle() {
        page1btn.getStyleClass().add("round-background");
        page2btn.getStyleClass().add("basic-hover");
    }

    void initAnalysis() {
        factors = new Factors();
        pair = new Pair<>();
    }

    void initBatchTest() {
        batchTestFactors = new Factors();
        batchTestPair = new Pair<>();
    }

    void initComBox() {
        allocation.getItems().addAll("WF", "BF", "FF", "NF");
        priority.getItems().addAll("DMPO");
        allocation_batch.getItems().addAll("WF", "BF", "FF", "NF");
        priority_batch.getItems().addAll("DMPO");
        priority.setValue("DMPO");
        systemMode.getItems().addAll("LO", "HI", "ModeSwitch");
        RTM.getItems().addAll("MSRP", "MSRPNew", "Mrsp", "MrspNew", "PWLP", "Dynamic");
    }

    void resultTableChangeColorWithSort() {
        ObservableList<Object[]> data = table2.getItems();
        table2.setRowFactory(tv -> new TableRow<Object[]>() {
            @Override
            protected void updateItem(Object[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle(""); // 处理空行的情况
                } else {
                    int rowIndex = getIndex();
                    if (rowIndex >= 0 && rowIndex < data.size()) {
                        Object[] currentRow = data.get(rowIndex);
                        if (currentRow != null && currentRow.length > 0) {
                            String value = (String) currentRow[currentRow.length - 1];
//                            System.out.println(currentRow[0] + " " + value);
                            if (value.equals("Unschedulable")) {
                                setStyle("-fx-background-color: rgb(240, 211, 211);");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                }
            }
        });

        TableColumn<Object[], String> targetColumn = (TableColumn<Object[], String>) table2.getColumns().get(table2.getColumns().size() - 1);

        targetColumn.setCellFactory(column -> new TableCell<Object[], String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); // 确保单元格内容正常显示
                if (empty || item == null) {
                    setStyle(""); // 处理空单元格的情况
                } else {
                    if (item.equals("Accept")) {
                        // 在原有样式的基础上追加背景颜色样式
                        setStyle("-fx-text-fill: rgb(22, 202, 173);");
                    } else {
                        if (item.equals("Unschedulable")) setStyle("-fx-text-fill: rgb(173, 67, 62);");
                        else setStyle("");
                    }
                }
                setText(item);
            }
        });
    }

    /**
     * bind the data with the table column
     */
    void fillTable(TableView table, ObservableList<Object[]> data) {
        // 设置数据源到 TableView
        table.getItems().clear();
        table.getItems().addAll(data);

        // 将数据源中的数据与每个 TableColumn 进行绑定
        for (int i = 0; i < table.getColumns().size(); i++) {
            TableColumn<Object[], Object> column = (TableColumn<Object[], Object>) table.getColumns().get(i);

            int columnIndex = i;
            // 使用 lambda 表达式设置 CellValueFactory
            column.setCellValueFactory(cellData -> {
                Object[] rowData = cellData.getValue();
                if (rowData.length > columnIndex) {
                    Object cellValue = rowData[columnIndex];
                    return cellValue != null ? new SimpleObjectProperty<>(cellValue) : null;
                }
                return null;
            });
        }
    }

    void generateTasksTable(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();
        for (ArrayList<SporadicTask> sporadicTasks : tasks) {
            for (SporadicTask task : sporadicTasks) {
                DecimalFormat df = new DecimalFormat("#.###");
                String formattedValue = df.format(task.util);
                // id, partition, priority, critical level, period, deadline, WCET, resource access, utilization
                Object[] row = new Object[]{task.id, task.partition, task.priority, task.critical == 0 ? "LO" : "HI",
                        task.period, task.deadline, task.C_LOW, task.C_HIGH, task.resource_required_index, formattedValue};
                data.add(row);
            }
        }
        fillTable(table1, data);
    }

    void generateResourceAccessButton() {
        TableColumn<Object[], ArrayList> resourceAccess = (TableColumn<Object[], ArrayList>) table1.getColumns().get(table1.getColumns().size() - 2);
        resourceAccess.setCellFactory(createButtonCellFactory());
        resourceAccess.setText("Resource Access");
    }

    private Callback<TableColumn<Object[], ArrayList>, TableCell<Object[], ArrayList>> createButtonCellFactory() {
        return new Callback<TableColumn<Object[], ArrayList>, TableCell<Object[], ArrayList>>() {
            @Override
            public TableCell<Object[], ArrayList> call(final TableColumn<Object[], ArrayList> param) {
                return new TableCell<Object[], ArrayList>() {
                    private final Button button = new Button("View");

                    {
//                        button.setStyle("-fx-background-color: rgb(22, 202, 173);-fx-text-fill: white;-fx-focus-color: transparent;-fx-faint-focus-color: transparent;");
                        button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                "-fx-background-insets: 0, 1; -fx-faint-focus-color: transparent;" +
                                "-fx-background-radius: 5px; -fx-text-fill: white;" +
                                "-fx-font-weight: bold;-fx-font-size: 14px;");
                        button.setOnAction(event -> {
                            Object[] item = getTableView().getItems().get(getIndex());
                            taskClickCount = 1;
                            taskClicked(item);
                        });
                        // 鼠标进入时的样式
                        button.setOnMouseEntered(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(17, 183, 168), rgb(17, 152, 130));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });
                        // 鼠标退出时的样式
                        button.setOnMouseExited(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                    "-fx-background-insets: 0, 1; -fx-faint-focus-color: transparent;" +
                                    "-fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold;-fx-font-size: 14px;");
                        });

                        // 按下时的样式
                        button.setOnMousePressed(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(11, 101, 86), rgb(65, 122, 112));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });

                        // 恢复正常样式
                        button.setOnMouseReleased(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });
                    }

                    @Override
                    protected void updateItem(ArrayList item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(button);
                        }
                    }
                };
            }
        };
    }

    void generateResource(ArrayList<Resource> resources) {
        HBox contentHBox = new HBox();
        for (Resource resource : resources) {
            ResourcePaneController rpc = new ResourcePaneController(page11);
            rpc.getResourceId().setText("R" + (resource.id - 1));
            rpc.setID(resource.id);
            rpc.getTipForCsl().setText("length: " + resource.csl);

            contentHBox.getChildren().add(rpc);
        }
        pResource.setContent(contentHBox);
    }

    void generatePartition() {
        HBox contentHBox = new HBox();
        for (int i = 0; i < factors.TOTAL_PARTITIONS; i++) {
            ResourcePartitionController rpc = new ResourcePartitionController();
            rpc.getPartitionId().setText("P" + i);

            contentHBox.getChildren().add(rpc);
        }

        pPartition.setContent(contentHBox);
    }

//     双击控件弃用，换成按钮
//    /**
//     * link task table to resource and partition block by define a new row factory
//     * -> click table row twice to light the task
//     * and call demonstration of resource with access time and partition
//     **/
//    void taskClicked() {
//        for (Object[] item : table1.getItems()) {
//            if (item != null && item.length > 0) {
//                table1.setRowFactory(tv -> {
//                    TableRow<Object[]> row = new TableRow<>() {
//                        @Override
//                        protected void updateItem(Object[] item, boolean empty) {
//                            super.updateItem(item, empty);
//                            if (item == null || empty) setStyle("");
//                            else {
//                                if (taskShowing == (int) item[0]) setStyle("-fx-background-color: rgb(22, 202, 173);");
//                                else setStyle("");
//                            }
//                        }
//                    };
//                    row.setOnMouseClicked(event -> {
//                        if (event.getClickCount() == 2 && (!row.isEmpty())) {
//                            Object[] selectedItem = table1.getSelectionModel().getSelectedItem();
//                            System.out.println("Select No.   " + selectedItem[0]);
//                            if (taskShowing == (int) selectedItem[0]) {
//                                taskClickCount++;
//                            } else {
//                                taskClickCount = 1;
//                                // 恢复上一个双击行的背景颜色
//                                if (taskShowing != -1) {
//                                    table1.refresh();
//                                    taskClickedShowResourceAndPartition(taskShowing, true);
//                                }
//                            }
//                            if (taskClickCount % 2 == 0) {
//                                // 双击同一行两次，恢复原样
//                                taskClickedShowResourceAndPartition(taskShowing, true);
//                                table1.refresh();
//                                taskShowing = -1;
//                            } else {
//                                // 更改当前行的背景颜色为绿色
//                                taskShowing = (int) selectedItem[0];
//                                table1.refresh();
//                                taskClickedShowResourceAndPartition(taskShowing, false);
//                            }
//                        }
//                        taskClickedFactoryAwake = true;
//                        System.out.println("task click awake!!！");
//                    });
//                    return row;
//                });
//            }
//        }
//    }

    void cleanStyleBeforeChange(String styleToBeCleaned) {
        // clean first
        HBox partitionPane = (HBox) pPartition.getContent();
        for (int i = 0; i < partitionPane.getChildren().size(); i++) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(i);
            rpc.backToNormal(styleToBeCleaned);
        }
        HBox resourcePane = (HBox) pResource.getContent();
        for (int i = 0; i < resourcePane.getChildren().size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(i);
            rpc.backToNormal(styleToBeCleaned);
        }
    }

    void onWakeUpTaskClicked() {
        table1.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !taskClickedFactoryAwake) {
                // clean first
                cleanStyleBeforeChange("resource-clicked-change-all");

//                taskClicked();
                table1.refresh();
            }
        });
    }

    /**
     * click resource access button to show
     **/
    void taskClicked(Object[] rowItem) {
        if (taskShowing == (int) rowItem[0]) {
            taskClickCount = 0;
        } else {
            taskClickCount = 1;
            // 恢复上一个双击行的背景颜色
            if (taskShowing != -1) {
                table1.refresh();
                taskClickedShowResourceAndPartition(taskShowing, true);
            }
        }
        if (taskClickCount == 0) {
            taskClickedShowResourceAndPartition(taskShowing, true);
            table1.refresh();
            taskShowing = -1;
        } else {
            // 更改当前行的背景颜色为绿色
            taskShowing = (int) rowItem[0];
            table1.refresh();
            taskClickedShowResourceAndPartition(taskShowing, false);
        }
        table1.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>() {
                @Override
                protected void updateItem(Object[] item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) setStyle("");
                    else {
                        if (taskShowing == (int) item[0]) setStyle("-fx-background-color: rgb(22, 202, 173);");
                        else setStyle("");
                    }
                }
            };

            return row;
        });
    }

    /**
     * link task table to resource and partition block
     * -> show the resource with access time and partition
     * -> as well as restore resource and partition back to normal
     **/
    void taskClickedShowResourceAndPartition(int taskId, boolean restore) {
        ArrayList<ArrayList<SporadicTask>> tasks = pair.getFirst();
        ArrayList<Integer> resourceBlock = new ArrayList<>();
        ArrayList<Integer> numberOfAccessInOneRelease = new ArrayList<>();
        int partitionBlock = -1;
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                if (tasks.get(i).get(j).id == taskId) {
                    SporadicTask task = tasks.get(i).get(j);
                    resourceBlock = task.resource_required_index;
                    numberOfAccessInOneRelease = task.number_of_access_in_one_release;
                    partitionBlock = i;
                }
            }
        }
        // show resource
        HBox resourcePane = (HBox) pResource.getContent();
        if (resourceBlock.size() == 0 && restore) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(0);
            rpc.backToNormal("resource-change-when-task-clicked");
            rpc.getTaskAccessTime().setText("--");
        }
        for (int i = 0; i < resourceBlock.size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(resourceBlock.get(i));
            if (restore) {
                rpc.backToNormal("resource-change-when-task-clicked");
                rpc.getTaskAccessTime().setText("--");
                rpc.getTipForCsl().setText("length: --");
            } else {
                rpc.getTaskAccessTime().setText(String.valueOf(numberOfAccessInOneRelease.get(i)));
                rpc.onRespondTask();
            }
        }
        // show partition
        HBox partitionPane = (HBox) pPartition.getContent();
        ResourcePartitionController rac = (ResourcePartitionController) partitionPane.getChildren().get(partitionBlock);
        if (restore) rac.backToNormal("resource-change-when-task-clicked");
        else rac.onRespond("resource-change-when-task-clicked");
    }

    /**
     * link resource panel to task table and partition block
     * -> light the corresponding tasks and partition
     * -> as well as task table and partition back to normal
     **/
    void resourceClicked() {
        HBox resourcePane = (HBox) pResource.getContent();
        for (int i = 0; i < resourcePane.getChildren().size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(i);
            rpc.setOnMouseClicked(event -> {
                // clean first
                cleanStyleBeforeChange("resource-change-when-task-clicked");

                int lastShowingResource = currentShowingResource;
                resourceClickedShowPartition(true);
                resourceClickedShowTask(true);

                currentShowingResource = rpc.onPaneClicked();

                if (lastShowingResource == currentShowingResource) resourceClickCount++;
                else resourceClickCount = 1;

                if (resourceClickCount % 2 == 0) {
                    resourceClickedShowPartition(true);
                    resourceClickedShowTask(true);
                } else {
                    resourceClickedShowPartition(false);
                    resourceClickedShowTask(false);
                }
                taskClickedFactoryAwake = false;
                System.out.println("task click sleep...");
            });
        }
    }

    void resourceClickedShowPartition(boolean restore) {
        // show partition
        if (currentShowingResource == -1 && restore) return;
        ArrayList<Integer> partitions = pair.getSecond().get(currentShowingResource - 1).partitions;
        HBox partitionPane = (HBox) pPartition.getContent();
        System.out.println(partitions);
        if (partitions.size() == 0) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(0);
            rpc.backToNormal("resource-clicked-change-all");
        }

        for (Integer partition : partitions) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(partition);
            if (restore) rpc.backToNormal("resource-clicked-change-all");
            else rpc.onRespond("resource-clicked-change-all");
        }
    }

    void resourceClickedShowTask(boolean restore) {
        //show task
        if (currentShowingResource == -1 && restore) return;
        HashSet<Integer> tasksId = new HashSet<>();
        ArrayList<SporadicTask> tasks = pair.getSecond().get(currentShowingResource - 1).requested_tasks;
        for (SporadicTask task : tasks) tasksId.add(task.id);
        System.out.println(tasksId);
        if (restore) table1.setRowFactory(null);
        else {
            for (Object[] item : table1.getItems()) {
                if (item != null && item.length > 0) {
                    table1.setRowFactory(tv -> new TableRow<>() {
                        @Override
                        protected void updateItem(Object[] item1, boolean empty) {
                            super.updateItem(item1, empty);
                            if (item1 == null || empty) {
                                setStyle("");
                            } else {
                                if (tasksId.contains((int) item1[0])) {
                                    setStyle("-fx-background-color: linear-gradient(to bottom, rgb(255, 239, 193), rgb(255, 218, 105));");
                                } else {
                                    setStyle("");
                                }
                            }
                        }
                    });
                }
            }
        }
        table1.refresh();
    }

    void generateResponseTimeTable(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();
        for (ArrayList<SporadicTask> sporadicTasks : tasks) {
            for (SporadicTask task : sporadicTasks) {
                String schedulable = "Unresolved";
                if (task.schedulable == 1) schedulable = "Accept";
                else if (task.schedulable == 0) schedulable = "Unschedulable";
                if (systemMode.getValue() == "ModeSwitch" || systemMode.getValue() == "HI") {
                    long Ri = task.Ri_Switch;
                    if (systemMode.getValue() == "HI") Ri = task.Ri_HI;
                    if (task.critical == 1) {     //只显示HI任务
                        // id, partition, priority, response time, deadline, WCET
                        // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                        Object[] row = new Object[]{task.id, task.partition, "HI", task.priority, Ri, task.deadline, task.WCET,
                                task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local, task.PWLP_S, schedulable};
                        data.add(row);
                    }
                } else {
                    // id, partition, priority, response time, deadline, WCET
                    // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                    Object[] row = new Object[]{task.id, task.partition, task.critical == 0 ? "LO" : "HI", task.priority, task.Ri_LO, task.deadline, task.WCET,
                            task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local, task.PWLP_S, schedulable};
                    data.add(row);
                }
            }
        }
        fillTable(table2, data);
    }

    @FXML
    void onStartAnalysis() {
        if (loading.isVisible()) return;
        if (!generateDone) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }
        if (systemMode.getValue() == null || RTM.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("System parameters are incomplete.");
            alert.showAndWait();
            return;
        }
        factors.SYSTEM_MODE = systemMode.getValue();
        factors.ANALYSIS_MODE = RTM.getValue();

        /* set the start icon inaccessible */
        startLabel.setVisible(false);
        loading.setVisible(true);

        Analysis analysis = new Analysis();
        analysis.analysis(factors, pair.getFirst(), pair.getSecond());
        generateResponseTimeTable(pair.getFirst());

        showSchedulable(factors.schedulable);

        /* set the start icon accessible */
        startLabel.setVisible(true);
        loading.setVisible(false);
    }

    @FXML
    void onGenerate() {
        if (generating.isVisible()) return;

        initAnalysis();

        if (rangeT1.getText().isEmpty() || rangeT2.getText().isEmpty() || coreNum.getText().isEmpty() || rangeCSL1.getText().isEmpty() || rangeCSL2.getText().isEmpty()
                || maxAccess.getText().isEmpty() || taskNum.getText().isEmpty() || utility.getText().isEmpty() || resourceNum.getText().isEmpty()
                || rsf.getText().isEmpty() || allocation.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }


        factors.MIN_PERIOD = Integer.parseInt(judgeInteger(rangeT1));
        factors.MAX_PERIOD = Integer.parseInt(judgeInteger(rangeT2));
        factors.TOTAL_PARTITIONS = Integer.parseInt(judgeInteger(coreNum));
        if (Double.parseDouble(judgeFloat(utility)) < factors.TOTAL_PARTITIONS * 0.1 || Double.parseDouble(judgeFloat(utility)) > factors.TOTAL_PARTITIONS * 0.7) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Utilization rate out of bounds. The range should be between 10% and 70% of the core count.");
            alert.showAndWait();
            return;
        }
        factors.CL_RANGE_LOW = Integer.parseInt(judgeInteger(rangeCSL1));
        factors.CL_RANGE_HIGH = Integer.parseInt(judgeInteger(rangeCSL2));
        factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = Integer.parseInt(judgeInteger(maxAccess));
        factors.NUMBER_OF_TASKS = Integer.parseInt(judgeInteger(taskNum));
        factors.UTILISATION = Double.parseDouble(judgeFloat(utility)) < factors.TOTAL_PARTITIONS ? Double.parseDouble(judgeFloat(utility)) : factors.TOTAL_PARTITIONS;
        factors.ALLOCATION = allocation.getValue();

        factors.RESOURCE_SHARING_FACTOR = Math.min(Double.parseDouble(judgeFloat(rsf)), 1.0);
        factors.TOTAL_RESOURCES = Integer.parseInt(judgeInteger(resourceNum));
        factors.PRIORITY = priority.getValue();

        if (factors.MIN_PERIOD == -1 || factors.MAX_PERIOD == -1 || factors.TOTAL_PARTITIONS == -1 || factors.CL_RANGE_LOW == -1 || factors.CL_RANGE_HIGH == -1 ||
                factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE == -1 || factors.NUMBER_OF_TASKS == -1 || factors.UTILISATION == -1 || factors.TOTAL_RESOURCES == -1 ||
                factors.RESOURCE_SHARING_FACTOR == -1 || allocation.getValue() == null)
            return;


        /* set the start icon inaccessible */
        generateLabel.setVisible(false);
        generating.setVisible(true);

        Analysis analysis = new Analysis();
        pair = analysis.generateSystem(factors);


        generateTasksTable(pair.getFirst());
//        fillTable3(pair.getSecond());
        generateResource(pair.getSecond());
        generatePartition();

        generateDone = true;

        /* set the start icon accessible */
        generateLabel.setVisible(true);
        generating.setVisible(false);

        // show data on the screen
//        onWakeUpTaskClicked();
//        taskClicked();
        generateResourceAccessButton();
        resourceClicked();
    }

    void showSchedulable(boolean schedulable) {
        if (schedulable) {
            scheduleYES.setVisible(true);
            scheduleNO.setVisible(false);
        } else {
            scheduleNO.setVisible(true);
            scheduleYES.setVisible(false);
        }
    }

    @FXML
    void onChangeToPage1() {
        page1btn.getStyleClass().add("round-background");
        page2btn.getStyleClass().remove("round-background");
        page3btn.getStyleClass().remove("round-background");

        page11.setVisible(true);
        page12.setVisible(true);
        page2.setVisible(false);
        page3.setVisible(false);
    }

    @FXML
    void onChangeToPage2() {
        page1btn.getStyleClass().remove("round-background");
        page2btn.getStyleClass().add("round-background");
        page3btn.getStyleClass().remove("round-background");

        page11.setVisible(false);
        page12.setVisible(false);
        page2.setVisible(true);
        page3.setVisible(false);
    }

    @FXML
    void onChangeToPage3() {
        page1btn.getStyleClass().remove("round-background");
        page2btn.getStyleClass().remove("round-background");
        page3btn.getStyleClass().add("round-background");


        page11.setVisible(false);
        page12.setVisible(false);
        page2.setVisible(false);
        page3.setVisible(true);

    }

    private void initConfig() {
        config.setOnMouseClicked(e -> openConfigWindow());
    }

    @FXML
    private void openConfigWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("config.fxml"));
            Parent root = loader.load();

            Stage popupStage = new Stage();
            popupStage.initModality(Modality.APPLICATION_MODAL);
            popupStage.setTitle("Config");

            Scene scene = new Scene(root);
            popupStage.setScene(scene);
            popupStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    @FXML
    private void handleAddMethod() {
        String selectedMethod = methodComboBox.getValue();
        if (selectedMethod != null && !selectedMethod.isEmpty() && !selectedMethodsList.getItems().contains(selectedMethod)) {
            selectedMethodsList.getItems().add(selectedMethod);
        }
    }

    @FXML
    private void handleRemoveMethod() {
        int selectedIndex = selectedMethodsList.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            selectedMethodsList.getItems().remove(selectedIndex);
        }
    }


    @FXML
    private void onReset(){
        barChart1.getData().clear();
        barChart2.getData().clear();
        barChart3.getData().clear();
    }


    /** batch test setting -> input + method -> system number Analysis test**/
    @FXML
    private void onBatchTest() {
        initBatchTest();
        int sysNum = Integer.parseInt(judgeInteger(sysNum_batch));
        if (sysNum_batch.getText().isEmpty() || rangeT1_batch.getText().isEmpty() || rangeT2_batch.getText().isEmpty() || coreNum_batch.getText().isEmpty() || rangeCSL1_batch.getText().isEmpty() || rangeCSL2_batch.getText().isEmpty()
                || maxAccess_batch.getText().isEmpty() || taskNum_batch.getText().isEmpty() || utility_batch.getText().isEmpty() || resourceNum_batch.getText().isEmpty()
                || rsf_batch.getText().isEmpty() || allocation_batch.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }


        batchTestFactors.MIN_PERIOD = Integer.parseInt(judgeInteger(rangeT1_batch));
        batchTestFactors.MAX_PERIOD = Integer.parseInt(judgeInteger(rangeT2_batch));
        batchTestFactors.TOTAL_PARTITIONS = Integer.parseInt(judgeInteger(coreNum_batch));
        if (Double.parseDouble(judgeFloat(utility_batch)) < batchTestFactors.TOTAL_PARTITIONS * 0.1 || Double.parseDouble(judgeFloat(utility_batch)) > batchTestFactors.TOTAL_PARTITIONS * 0.7) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Utilization rate out of bounds. The range should be between 10% and 70% of the core count.");
            alert.showAndWait();
            return;
        }
        batchTestFactors.CL_RANGE_LOW = Integer.parseInt(judgeInteger(rangeCSL1_batch));
        batchTestFactors.CL_RANGE_HIGH = Integer.parseInt(judgeInteger(rangeCSL2_batch));
        batchTestFactors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = Integer.parseInt(judgeInteger(maxAccess_batch));
        batchTestFactors.NUMBER_OF_TASKS = Integer.parseInt(judgeInteger(taskNum_batch));
        batchTestFactors.UTILISATION = Double.parseDouble(judgeFloat(utility_batch)) < batchTestFactors.TOTAL_PARTITIONS ? Double.parseDouble(judgeFloat(utility_batch)) : batchTestFactors.TOTAL_PARTITIONS;
        batchTestFactors.ALLOCATION = allocation_batch.getValue();

        batchTestFactors.RESOURCE_SHARING_FACTOR = Math.min(Double.parseDouble(judgeFloat(rsf_batch)), 1.0);
        batchTestFactors.TOTAL_RESOURCES = Integer.parseInt(judgeInteger(resourceNum_batch));
        batchTestFactors.PRIORITY = priority_batch.getValue();

        if (batchTestFactors.MIN_PERIOD == -1 || batchTestFactors.MAX_PERIOD == -1 || batchTestFactors.TOTAL_PARTITIONS == -1 || batchTestFactors.CL_RANGE_LOW == -1 || batchTestFactors.CL_RANGE_HIGH == -1 ||
                batchTestFactors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE == -1 || batchTestFactors.NUMBER_OF_TASKS == -1 || batchTestFactors.UTILISATION == -1 || batchTestFactors.TOTAL_RESOURCES == -1 ||
                batchTestFactors.RESOURCE_SHARING_FACTOR == -1 || batchTestFactors.ALLOCATION == null)
            return;

        onReset();


        var batchTestAnalyzer = new Analysis();

        // selectedMethodsList

        //int[][] res = new int[3][3];

        // 获取对比方法
        ObservableList<String> methods = selectedMethodsList.getItems();


        ArrayList<String> compareMethod = new ArrayList<>();

        for (String method : methods) {
            System.out.println(method);
            switch (method){
                case "MSRP_Original":
                    compareMethod.add("MSRP");
                    break;
                case "MSRP_Holistic":
                    compareMethod.add("MSRPNew");
                    break;
                case "PWLP_Holistic":
                    compareMethod.add("PWLP");
                    break;
                case "Mrsp_Original":
                    compareMethod.add("Mrsp");
                    break;
                case "Mrsp_Holistic":
                    compareMethod.add("MrspNew");
                    break;
                case "Dynamic":
                    compareMethod.add("Dynamic");
                    break;
            }
        }

        this.result = batchTestAnalyzer.batchAnalysis(batchTestFactors, sysNum, compareMethod);
        this.currentIndex = 0;

        //updateChartData();

        // 初始化方法映射的 HashMap
        Map<String, String> methodMap = new HashMap<>();

        // 添加映射
        methodMap.put("MSRP", "MSRP_Original");
        methodMap.put("MSRPNew", "MSRP_Holistic");
        methodMap.put("PWLP", "PWLP_Holistic");
        methodMap.put("Mrsp", "Mrsp_Original");
        methodMap.put("MrspNew", "Mrsp_Holistic");
        methodMap.put("Dynamic", "Dynamic");

        // 添加新数据
        Map<String, Double> res = result.get(0);

        for (Map.Entry<String, Double> entry : res.entrySet()) {
            String labelName = methodMap.get(entry.getKey());
            createBar(barChart1, labelName, entry.getValue());
        }
        barChart1.setBarGap(25);

        // 添加新数据
        res = result.get(1);

        for (Map.Entry<String, Double> entry : res.entrySet()) {
            String labelName = methodMap.get(entry.getKey());
            createBar(barChart2, labelName, entry.getValue());
        }
        barChart2.setBarGap(25);

        // 添加新数据
        res = result.get(2);

        for (Map.Entry<String, Double> entry : res.entrySet()) {
            String labelName = methodMap.get(entry.getKey());
            createBar(barChart3, labelName, entry.getValue());
        }
        barChart3.setBarGap(25);


        String[] COLOR_SCHEME = {"#000080CC", "#ecb01fCC", "#76ab2fCC", "#0071bcCC", "#7d2e8dCC"};
        int index = 0;
        for (XYChart.Series<String, Number> series : barChart1.getData()) {
            for(XYChart.Data data : series.getData())
            {
                String style = "-fx-bar-fill: " + COLOR_SCHEME[index % COLOR_SCHEME.length] + ";";
                //data.getNode().setStyle("-fx-bar-fill: #e3d503;");
                data.getNode().setStyle(style);
            }
            index++;

        }


        // 设置图例颜色
        List<Node> legendItems = new ArrayList<>(barChart1.lookupAll("Label.chart-legend-item"));
        for (int i = 0; i < legendItems.size(); i++) {
            Label label = (Label) legendItems.get(i);
            if (label != null && label.getGraphic() != null) {
                String color = COLOR_SCHEME[i % COLOR_SCHEME.length]; // 循环使用颜色
                label.getGraphic().setStyle("-fx-background-color: " + color + ";");
            }
        }

        index = 0;
        for (XYChart.Series<String, Number> series : barChart2.getData()) {
            for(XYChart.Data data : series.getData())
            {
                String style = "-fx-bar-fill: " + COLOR_SCHEME[index % COLOR_SCHEME.length] + ";";
                //data.getNode().setStyle("-fx-bar-fill: #e3d503;");
                data.getNode().setStyle(style);
            }
            index++;

        }


        // 设置图例颜色
        legendItems = new ArrayList<>(barChart2.lookupAll("Label.chart-legend-item"));
        for (int i = 0; i < legendItems.size(); i++) {
            Label label = (Label) legendItems.get(i);
            if (label != null && label.getGraphic() != null) {
                String color = COLOR_SCHEME[i % COLOR_SCHEME.length]; // 循环使用颜色
                label.getGraphic().setStyle("-fx-background-color: " + color + ";");
            }
        }

        index = 0;
        for (XYChart.Series<String, Number> series : barChart3.getData()) {
            for(XYChart.Data data : series.getData())
            {
                String style = "-fx-bar-fill: " + COLOR_SCHEME[index % COLOR_SCHEME.length] + ";";
                //data.getNode().setStyle("-fx-bar-fill: #e3d503;");
                data.getNode().setStyle(style);
            }
            index++;

        }

        // 设置图例颜色
        legendItems = new ArrayList<>(barChart3.lookupAll("Label.chart-legend-item"));
        for (int i = 0; i < legendItems.size(); i++) {
            Label label = (Label) legendItems.get(i);
            if (label != null && label.getGraphic() != null) {
                String color = COLOR_SCHEME[i % COLOR_SCHEME.length]; // 循环使用颜色
                label.getGraphic().setStyle("-fx-background-color: " + color + ";");
            }
        }




    }


//    public void createBar(BarChart<String, Number> barChart, String str, double num) {
//        XYChart.Series<String, Number> series = new XYChart.Series<>();
//        series.setName(str);
//        series.getData().add(new XYChart.Data<>("", num));
//        barChart.getData().add(series);
//
//    }


    public void createBar(BarChart<String, Number> barChart, String methodName, double value) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(methodName);
        XYChart.Data<String, Number> data = new XYChart.Data<>("", value);

        series.getData().add(data);
        barChart.getData().add(series);

    }







//    void initConfigUi(){
//        FIFONP_LOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(FIFONP_LOCK_Label,FIFONP_LOCK_HBox));
//        FIFONP_LOCK_Button.setOnMouseClicked(e->onclickedConfigButton(FIFONP_LOCK_Label,FIFONP_LOCK_TextField,FIFONP_LOCK_HBox));
//
//        FIFONP_UNLOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(FIFONP_UNLOCK_Label,FIFONP_UNLOCK_HBox));
//        FIFONP_UNLOCK_Button.setOnMouseClicked(e->onclickedConfigButton(FIFONP_UNLOCK_Label,FIFONP_UNLOCK_TextField,FIFONP_UNLOCK_HBox));
//
//        FIFOP_CANCEL_Label.setOnMouseClicked(e->onClickedConfigLabel(FIFOP_CANCEL_Label,FIFOP_CANCEL_HBox));
//        FIFOP_CANCEL_Button.setOnMouseClicked(e->onclickedConfigButton(FIFOP_CANCEL_Label,FIFOP_CANCEL_TextField,FIFOP_CANCEL_HBox));
//
//        FIFOP_LOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(FIFOP_LOCK_Label,FIFOP_LOCK_HBox));
//        FIFOP_LOCK_Button.setOnMouseClicked(e->onclickedConfigButton(FIFOP_LOCK_Label,FIFOP_LOCK_TextField,FIFOP_LOCK_HBox));
//
//        FIFOP_UNLOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(FIFOP_UNLOCK_Label,FIFOP_UNLOCK_HBox));
//        FIFOP_UNLOCK_Button.setOnMouseClicked(e->onclickedConfigButton(FIFOP_UNLOCK_Label,FIFOP_UNLOCK_TextField,FIFOP_UNLOCK_HBox));
//
//        Mrsp_LOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(Mrsp_LOCK_Label,Mrsp_LOCK_HBox));
//        Mrsp_LOCK_Button.setOnMouseClicked(e->onclickedConfigButton(Mrsp_LOCK_Label,Mrsp_LOCK_TextField,Mrsp_LOCK_HBox));
//
//        Mrsp_UNLOCK_Label.setOnMouseClicked(e->onClickedConfigLabel(Mrsp_UNLOCK_Label,Mrsp_UNLOCK_HBox));
//        Mrsp_UNLOCK_Button.setOnMouseClicked(e->onclickedConfigButton(Mrsp_UNLOCK_Label,Mrsp_UNLOCK_TextField,Mrsp_UNLOCK_HBox));
//
//        Mrsp_MIGRATION_Label.setOnMouseClicked(e->onClickedConfigLabel(Mrsp_MIGRATION_Label,Mrsp_MIGRATION_HBox));
//        Mrsp_MIGRATION_Button.setOnMouseClicked(e->onclickedConfigButton(Mrsp_MIGRATION_Label,Mrsp_MIGRATION_TextField,Mrsp_MIGRATION_HBox));
//
//        FULL_CONTEXT_SWITCH2_Label.setOnMouseClicked(e->onClickedConfigLabel(FULL_CONTEXT_SWITCH2_Label,FULL_CONTEXT_SWITCH2_HBox));
//        FULL_CONTEXT_SWITCH2_Button.setOnMouseClicked(e->onclickedConfigButton(FULL_CONTEXT_SWITCH2_Label,FULL_CONTEXT_SWITCH2_TextField,FULL_CONTEXT_SWITCH2_HBox));
//    }
//    void onClickedConfigLabel(Label label,HBox hBox){
//        label.setVisible(false);
//        hBox.setVisible(true);
//    }
//
//    void onclickedConfigButton(Label label,TextField textField,HBox hBox){
//        if(parseDouble(FIFONP_LOCK_TextField)==Double.MIN_VALUE){   //不合法字符
//            textField.setText("illegal value");
//            // 创建一个 PauseTransition 对象，设置持续时间为0.5秒
//            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
//            pauseTransition.setOnFinished(e -> {
//                // 当暂停结束后执行的操作
//                label.setVisible(true);
//                hBox.setVisible(false);
//            });
//            pauseTransition.play();
//        }
//        else{
//            AnalysisUtils.FIFONP_LOCK = parseDouble(text_FIFONP_LOCK);
//            textField.setText("successful");
//            // 创建一个 PauseTransition 对象，设置持续时间为0.5秒
//            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
//            pauseTransition.setOnFinished(e -> {
//                // 当暂停结束后执行的操作
//                label.setVisible(true);
//                hBox.setVisible(false);
//            });
//            pauseTransition.play();
//        }
//    }
}

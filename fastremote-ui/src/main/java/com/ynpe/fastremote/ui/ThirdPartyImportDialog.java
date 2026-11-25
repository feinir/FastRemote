// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.i18n.Texts;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.fxml.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Owns the source snapshot, background-operation generation and preview lifecycle. */
public final class ThirdPartyImportDialog {
    @FXML private Button localButton,fileButton,folderButton,keyButton;
    @FXML private Label sourceLabel,status;
    @FXML private TableView<Row> table;
    @FXML private TextField missingUsername;
    @FXML private CheckBox skipDuplicates,acknowledge;
    private HostCatalogWorkflow workflow;
    private HostImportSource source;
    private Dialog<Void> dialog;
    private HostImportPlan plan;
    private Supplier<CompletionStage<Boolean>> unlock;
    private Runnable refreshed;
    private Button importButton;
    private boolean closed,importing,busy;
    private long generation;

    public static void show(Window owner,HostImportSource source,HostCatalogWorkflow workflow,
                            Supplier<CompletionStage<Boolean>> unlock,Runnable refreshed) {
        FXMLLoader loader=new FXMLLoader(ThirdPartyImportDialog.class.getResource("ThirdPartyImportView.fxml"),Texts.localizer().bundle());
        try {
            Parent view=loader.load();ThirdPartyImportDialog controller=loader.getController();
            controller.open(owner,view,source,workflow,unlock,refreshed);
        }catch(IOException failure){throw new IllegalStateException("Cannot load import dialog",failure);}
    }
    @FXML private void initialize(){
        TableColumn<Row,Boolean> selected=new TableColumn<>(Texts.text("migration.select"));
        selected.setCellValueFactory(c->c.getValue().selected);
        selected.setCellFactory(c->new CheckBoxTableCell<>(){
            @Override public void updateItem(Boolean item,boolean empty){super.updateItem(item,empty);setDisable(empty||getTableRow()==null||getTableRow().getItem()==null||!getTableRow().getItem().entry.importable());}
        });selected.setPrefWidth(54);
        var name=column("migration.name",r->r.entry.name(),150);
        var group=column("migration.group",r->r.entry.group(),110);
        var host=column("migration.host",r->r.entry.host()+":"+r.entry.port(),170);
        TableColumn<Row,String> user=new TableColumn<>(Texts.text("migration.username"));user.setCellValueFactory(c->c.getValue().username);user.setCellFactory(TextFieldTableCell.forTableColumn());user.setPrefWidth(100);
        var state=column("migration.state",r->r.entry.notices().stream().map(n->Texts.text("migration.notice."+n.name())).reduce((a,b)->a+"; "+b).orElse(Texts.text("migration.ready")),230);
        table.getColumns().setAll(List.of(selected,name,group,host,user,state));
        table.setPlaceholder(new Label(Texts.text("migration.empty")));
        keyButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((a,b,c)->updateEnabled());
        missingUsername.textProperty().addListener((a,b,c)->updateEnabled());
        acknowledge.selectedProperty().addListener((a,b,c)->updateEnabled());
    }
    private static TableColumn<Row,String> column(String key,java.util.function.Function<Row,String> value,double width){
        TableColumn<Row,String> c=new TableColumn<>(Texts.text(key));c.setCellValueFactory(v->new ReadOnlyStringWrapper(value.apply(v.getValue())));c.setPrefWidth(width);c.setEditable(false);
        c.setCellFactory(ignored->new TableCell<>() {
            @Override protected void updateItem(String value,boolean empty) {
                super.updateItem(value,empty);setText(empty?null:value);
                setTooltip(empty||value==null||value.isBlank()?null:new Tooltip(value));
            }
        });return c;
    }
    private void open(Window owner,Parent view,HostImportSource source,HostCatalogWorkflow workflow,
                      Supplier<CompletionStage<Boolean>> unlock,Runnable refreshed){
        this.source=source;this.workflow=workflow;this.unlock=unlock;this.refreshed=refreshed;
        dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle(Texts.text("migration.title")+" · "+source.label());
        dialog.getDialogPane().setContent(view);dialog.setResizable(true);
        ButtonType apply=new ButtonType(Texts.text("migration.import"),ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,apply);
        importButton=(Button)dialog.getDialogPane().lookupButton(apply);
        importButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();performImport();});
        localButton.setVisible(source==HostImportSource.TERMIUS||source==HostImportSource.PUTTY);localButton.setManaged(localButton.isVisible());
        fileButton.setVisible(source!=HostImportSource.TERMIUS);fileButton.setManaged(fileButton.isVisible());
        sourceLabel.setText(Texts.text("migration.source."+source.name()));
        acknowledge.setVisible(false);acknowledge.setManaged(false);
        DialogSpacing.configureResizableWindowGeometry(dialog,960,550,680,400);DialogSpacing.prepare(dialog);
        dialog.setOnCloseRequest(e->{if(importing)e.consume();});
        dialog.setOnHidden(e->{closed=true;generation++;clearPlan();});
        updateEnabled();dialog.show();
    }
    @FXML private void readLocal(){load(Optional.empty());}
    @FXML private void chooseFile(){
        FileChooser chooser=new FileChooser();chooser.setTitle(Texts.text("migration.file"));
        String[] extensions=switch(source){case MOBAXTERM->new String[]{"*.ini","*.mxtsessions"};case XSHELL->new String[]{"*.xts","*.xsh","*.xfp"};case PUTTY->new String[]{"*.reg","*"};case BITVISE->new String[]{"*.tlp","*.bscp"};default->new String[]{"*"};};
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(source.label(),extensions));
        var file=PlatformFileChooser.showOpenDialog(chooser,dialog.getOwner());if(file!=null)load(Optional.of(file.toPath()));
    }
    @FXML private void chooseFolder(){
        DirectoryChooser chooser=new DirectoryChooser();chooser.setTitle(Texts.text("migration.folder"));
        var directory=PlatformFileChooser.showDialog(chooser,dialog.getOwner());if(directory!=null)load(Optional.of(directory.toPath()));
    }
    private void load(Optional<Path> path){
        clearPlan();long request=++generation;setBusy(true);status.setText(Texts.text("migration.reading"));
        workflow.inspectThirdParty(source,path).whenComplete((result,error)->Platform.runLater(()->{
            if(closed||generation!=request){if(result!=null)result.close();return;}
            setBusy(false);
            if(error!=null){status.setText(Texts.text("migration.readFailed"));return;}
            plan=result;
            for(var entry:result.entries()){
                Row row=new Row(entry);row.selected.addListener((a,b,c)->updateEnabled());row.username.addListener((a,b,c)->updateEnabled());table.getItems().add(row);
            }
            boolean omitted=result.entries().stream().anyMatch(e->e.notices().contains(HostImportPlan.Notice.SETTINGS_OMITTED));
            acknowledge.setVisible(omitted);acknowledge.setManaged(omitted);acknowledge.setSelected(false);
            sourceLabel.setText(path.map(Path::toString).orElse(source.label()));
            status.setText(result.entries().size()+" "+Texts.text("migration.records"));updateEnabled();
        }));
    }
    @FXML private void attachKey(){
        Row row=table.getSelectionModel().getSelectedItem();
        if(row==null||plan==null||row.entry.authentication()!=com.ynpe.fastremote.domain.connection.AuthenticationType.PRIVATE_KEY)return;
        FileChooser chooser=new FileChooser();chooser.setTitle(Texts.text("migration.attachKey"));
        var file=PlatformFileChooser.showOpenDialog(chooser,dialog.getOwner());if(file==null)return;
        Dialog<String> password=new Dialog<>();password.initOwner(dialog.getDialogPane().getScene().getWindow());password.setTitle(Texts.text("migration.keyPassphrase"));
        PasswordField field=new PasswordField();field.setPromptText(Texts.text("migration.keyPassphraseOptional"));
        password.getDialogPane().setContent(field);password.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,ButtonType.OK);DialogSpacing.prepare(password);
        password.setResultConverter(button->button==ButtonType.OK?field.getText():null);
        Optional<String> entered=password.showAndWait();field.clear();if(entered.isEmpty())return;
        char[] secret=entered.get().toCharArray();long request=generation;HostImportPlan snapshot=plan;
        setBusy(true);status.setText(Texts.text("migration.reading"));
        CompletionStage<Void> operation;
        try{operation=workflow.attachImportKey(snapshot,row.entry.id(),file.toPath(),secret);}finally{Arrays.fill(secret,'\0');}
        operation.whenComplete((nothing,error)->Platform.runLater(()->{
            if(closed||generation!=request)return;setBusy(false);
            if(error!=null){status.setText(Texts.text("migration.keyFailed"));return;}
            row.entry=snapshot.entries().stream().filter(e->e.id().equals(row.entry.id())).findFirst().orElseThrow();
            table.refresh();status.setText(Texts.text("migration.ready"));updateEnabled();
        }));
    }
    private void performImport(){
        if(importButton.isDisabled()||plan==null)return;
        Set<String> selected=new HashSet<>();Map<String,String> users=new HashMap<>();
        for(Row row:table.getItems())if(row.selected.get()){
            selected.add(row.entry.id());String user=row.username.get().strip();users.put(row.entry.id(),user.isEmpty()?missingUsername.getText().strip():user);
        }
        importing=true;setBusy(true);status.setText(Texts.text("migration.importing"));
        CompletionStage<Boolean> ready=plan.hasSecrets()?unlock.get():java.util.concurrent.CompletableFuture.completedFuture(true);
        ready.whenComplete((ok,error)->Platform.runLater(()->{
            if(error!=null||!Boolean.TRUE.equals(ok)){importing=false;setBusy(false);status.setText(Texts.text("migration.cancelled"));return;}
            workflow.importThirdParty(plan,selected,users,skipDuplicates.isSelected()).whenComplete((result,failed)->Platform.runLater(()->{
                importing=false;setBusy(false);
                if(failed!=null){status.setText(Texts.text("migration.importFailed"));return;}
                clearPlan();refreshed.run();
                status.setText(Texts.text("migration.done")+" "+result.hosts()+"; "+Texts.text("migration.skipped")+" "+result.skipped());
                updateEnabled();
            }));
        }));
    }
    private void setBusy(boolean busy){this.busy=busy;localButton.setDisable(busy);fileButton.setDisable(busy);folderButton.setDisable(busy);table.setDisable(busy);missingUsername.setDisable(busy);skipDuplicates.setDisable(busy);acknowledge.setDisable(busy);keyButton.setDisable(busy);if(busy)importButton.setDisable(true);else updateEnabled();}
    private void updateEnabled(){
        if(importButton==null)return;
        Row active=table.getSelectionModel().getSelectedItem();
        keyButton.setDisable(busy||importing||plan==null||active==null||!active.entry.importable()||active.entry.authentication()!=com.ynpe.fastremote.domain.connection.AuthenticationType.PRIVATE_KEY);
        List<Row> selected=table.getItems().stream().filter(r->r.selected.get()).toList();
        importButton.setDisable(busy||importing||plan==null||selected.isEmpty()||selected.stream().anyMatch(r->!r.entry.importable()||(r.username.get().isBlank()&&missingUsername.getText().isBlank()))||(acknowledge.isVisible()&&!acknowledge.isSelected()));
    }
    private void clearPlan(){if(plan!=null){plan.close();plan=null;}table.getItems().clear();}
    static final class Row {
        HostImportPlan.Entry entry;final BooleanProperty selected;final StringProperty username;
        Row(HostImportPlan.Entry entry){this.entry=entry;selected=new SimpleBooleanProperty(entry.importable());username=new SimpleStringProperty(entry.username());}
    }
}

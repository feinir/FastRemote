// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.ui;

import com.ynpe.fastremote.domain.host.*;
import com.ynpe.fastremote.domain.connection.AuthenticationType;
import com.ynpe.fastremote.i18n.Texts;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ThirdPartyImportDialogTest {
    @BeforeAll static void toolkit()throws Exception {
        var ready=new CountDownLatch(1);
        try{Platform.startup(ready::countDown);}catch(IllegalStateException already){ready.countDown();}
        assertTrue(ready.await(5,TimeUnit.SECONDS));Platform.setImplicitExit(false);
    }
    @Test void requiresUsernameAndAdvancedSettingsAcknowledgment()throws Exception {
        var pending=new CompletableFuture<HostImportPlan>();var controller=fx(()->open(pending));
        try {
            fx(()->{invoke(controller,"readLocal");return null;});
            var entry=new HostImportPlan.Entry("one","Sample","","example.test",22,"",AuthenticationType.PRIVATE_KEY,"",List.of(HostImportPlan.Notice.USERNAME_REQUIRED,HostImportPlan.Notice.KEY_REQUIRED,HostImportPlan.Notice.SETTINGS_OMITTED),true);
            pending.complete(new HostImportPlan(HostImportSource.BITVISE,List.of(entry),Map.of()));
            fx(()->{
                Button apply=get(controller,"importButton");assertTrue(apply.isDisabled());
                TextField user=get(controller,"missingUsername");user.setText("deploy");assertTrue(apply.isDisabled());
                CheckBox ack=get(controller,"acknowledge");ack.setSelected(true);assertFalse(apply.isDisabled());
                TableView<?> table=get(controller,"table");table.getSelectionModel().select(0);
                Button key=get(controller,"keyButton");assertFalse(key.isDisabled());
                return null;
            });
        }finally{fx(()->{((Dialog<?>)get(controller,"dialog")).close();return null;});}
    }
    @Test void closesLatePreviewAndWipesSecretsAfterWindowCloses()throws Exception {
        var pending=new CompletableFuture<HostImportPlan>();var controller=fx(()->open(pending));
        fx(()->{invoke(controller,"readLocal");((Dialog<?>)get(controller,"dialog")).close();return null;});
        var secret=new HostImportPlan.Secret("synthetic-secret".toCharArray(),new char[0]);
        var result=new HostImportPlan(HostImportSource.TERMIUS,List.of(),Map.of("secret",secret));
        pending.complete(result);
        fx(()->{assertThrows(IllegalStateException.class,()->result.copySecret("secret"));assertEquals(0,secret.password()[0]);return null;});
    }
    @Test void replacingPreviewClosesPreviousSnapshot()throws Exception {
        var pending=new CompletableFuture<HostImportPlan>();var controller=fx(()->open(pending));
        var result=new HostImportPlan(HostImportSource.TERMIUS,List.of(),Map.of());
        try {
            fx(()->{invoke(controller,"readLocal");return null;});pending.complete(result);
            fx(()->{invoke(controller,"readLocal");assertThrows(IllegalStateException.class,()->result.copySecret("any"));return null;});
        }finally{fx(()->{((Dialog<?>)get(controller,"dialog")).close();return null;});}
    }
    private static ThirdPartyImportDialog open(CompletableFuture<HostImportPlan> pending)throws Exception {
        var loader=new FXMLLoader(ThirdPartyImportDialog.class.getResource("ThirdPartyImportView.fxml"),Texts.localizer().bundle());
        Parent root=loader.load();ThirdPartyImportDialog controller=loader.getController();
        HostCatalogWorkflow workflow=(HostCatalogWorkflow)Proxy.newProxyInstance(HostCatalogWorkflow.class.getClassLoader(),new Class[]{HostCatalogWorkflow.class},(proxy,method,args)->{
            if(method.getName().equals("inspectThirdParty"))return pending;
            throw new UnsupportedOperationException(method.getName());
        });
        var method=ThirdPartyImportDialog.class.getDeclaredMethod("open",Window.class,Parent.class,HostImportSource.class,HostCatalogWorkflow.class,Supplier.class,Runnable.class);
        method.setAccessible(true);method.invoke(controller,null,root,HostImportSource.TERMIUS,workflow,(Supplier<CompletionStage<Boolean>>)()->CompletableFuture.completedFuture(true),(Runnable)()->{});
        return controller;
    }
    static void invoke(Object object,String name)throws Exception{var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    @SuppressWarnings("unchecked") static <T>T get(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return(T)field.get(object);}
    static <T>T fx(Callable<T> callable)throws Exception{var task=new FutureTask<>(callable);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);}
}

package com.hzmalix.realm;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import io.realm.OrderedCollectionChangeSet;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

public class MainActivity extends AppCompatActivity {

    Realm uiThreadRealm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Realm.init(this); // context, usually an Activity or Application
        String realmName = "My Project";
        RealmConfiguration config = new RealmConfiguration.Builder().name(realmName).build();
        uiThreadRealm = Realm.getInstance(config);
        addChangeListenerToRealm(uiThreadRealm);
        FutureTask<String> Task = new FutureTask(new BackgroundQuickStart(), "test");
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.execute(Task);
    }

    private void addChangeListenerToRealm(Realm realm) {
        // all Tasks in the realm
        RealmResults<Task> Tasks = uiThreadRealm.where(Task.class).findAllAsync();
        Tasks.addChangeListener((collection, changeSet) -> {
            // process deletions in reverse order if maintaining parallel data structures
            // so indices don't change as you iterate
            OrderedCollectionChangeSet.Range[] deletions = changeSet.getDeletionRanges();
            for (OrderedCollectionChangeSet.Range range : deletions) {
                Log.v("QUICKSTART",
                        "Deleted range: " + range.startIndex + " to " + (range.startIndex + range.length - 1));
            }
            OrderedCollectionChangeSet.Range[] insertions = changeSet.getInsertionRanges();
            for (OrderedCollectionChangeSet.Range range : insertions) {
                Log.v("QUICKSTART",
                        "Inserted range: " + range.startIndex + " to " + (range.startIndex + range.length - 1));
            }
            OrderedCollectionChangeSet.Range[] modifications = changeSet.getChangeRanges();
            for (OrderedCollectionChangeSet.Range range : modifications) {
                Log.v("QUICKSTART",
                        "Updated range: " + range.startIndex + " to " + (range.startIndex + range.length - 1));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // the ui thread realm uses asynchronous transactions, so we can only safely close the realm
        // when the activity ends and we can safely assume that those transactions have completed
        uiThreadRealm.close();
    }

    public static class BackgroundQuickStart implements Runnable {
        @Override
        public void run() {
            String realmName = "My Project";
            RealmConfiguration config = new RealmConfiguration.Builder().name(realmName).build();
            Realm backgroundThreadRealm = Realm.getInstance(config);
            Task Task = new Task("New Task");
            backgroundThreadRealm.executeTransaction(transactionRealm -> {
                transactionRealm.insert(Task);
            });
            // all Tasks in the realm
            RealmResults<Task> Tasks = backgroundThreadRealm.where(Task.class).findAll();
            // you can also filter a collection
            RealmResults<Task> TasksThatBeginWithN = Tasks.where().beginsWith("name", "N").findAll();
            RealmResults<Task> openTasks = Tasks.where().equalTo("status", TaskStatus.Open.name()).findAll();
            Task otherTask = Tasks.get(0);
            // all modifications to a realm must happen inside of a write block
            backgroundThreadRealm.executeTransaction(transactionRealm -> {
                Task innerOtherTask = transactionRealm.where(Task.class).equalTo("_id", otherTask.getName()).findFirst();
                innerOtherTask.setStatus(TaskStatus.Complete);
            });
            Task yetAnotherTask = Tasks.get(0);
            String yetAnotherTaskName = yetAnotherTask.getName();
            // all modifications to a realm must happen inside of a write block
            backgroundThreadRealm.executeTransaction(transactionRealm -> {
                Task innerYetAnotherTask = transactionRealm.where(Task.class).equalTo("_id", yetAnotherTaskName).findFirst();
                innerYetAnotherTask.deleteFromRealm();
            });
            // because this background thread uses synchronous realm transactions, at this point all
            // transactions have completed and we can safely close the realm
            backgroundThreadRealm.close();
        }
    }

}

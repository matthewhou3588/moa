package moa.clusters.denstream;

import moa.core.AutoClassDiscovery;
import moa.core.AutoExpandVector;
import moa.evaluation.MeasureCollection;

import java.util.ArrayList;
import java.util.logging.Logger;

public class WithDBSCANTest {
    private WithDBSCANRun withDBSCANRun = null;
    private Thread withDBSCANRunThread = null;
    private Boolean running = false;




    public WithDBSCANTest() {

    }


    private void createwithDBSCANRunThread(){
        withDBSCANRun = new WithDBSCANRun();
        withDBSCANRunThread = new Thread(withDBSCANRun);
    }


    public void toggleState() {
        if(withDBSCANRun == null)
            createwithDBSCANRunThread();

        if(!withDBSCANRunThread.isAlive()){
            withDBSCANRunThread.start();
        }

        //pause
        if(running){
            running = false;
            withDBSCANRun.pause();
            withDBSCANRun.showClusterMetrics();

        }
        else{
            running = true;
            withDBSCANRun.resume();
        }
    }




    public static void main(String[] args) {
        WithDBSCANTest obj = new WithDBSCANTest();
        obj.toggleState();


//        for (MeasureCollection e : measureCollection) {
//        Class<?> clazz = (MeasureCollection)Class.forName("moa.evaluation.CMM");
//            System.out.println(e.class.getName());
//        }

    }

}

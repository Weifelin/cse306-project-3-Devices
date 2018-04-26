package osp.Devices;

/**
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Device extends IflDevice
{
    /**
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */
    private ArrayList<IORBQueue> queueList;
    private int currentUsingQueueIndex;
    private int currentOpenQueueIndex;
    private  static int lastCylinder;
    private static ConcurrentHashMap<ThreadCB,Vector<IORB>> iorb_by_thread_list; // for cancelling iorb for thread.

    public Device(int id, int numberOfBlocks)
    {
        // your code goes here
        super(id,numberOfBlocks);
        this.iorbQueue = new GenericList();
        currentOpenQueueIndex =0;
        currentUsingQueueIndex =0;
        //lastCylinder = 0;
        queueList = new ArrayList<>(1);
        for (int i=0; i<1; i++){
            queueList.add(new IORBQueue());
        }


    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init()
    {
        // your code goes here
        lastCylinder = 0;
        iorb_by_thread_list = new ConcurrentHashMap<>();
    }

    /**
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
    public int do_enqueueIORB(IORB iorb)
    {
        // your code goes here

        //preparation work
        PageTableEntry pageTableEntry = iorb.getPage();
        int locked = pageTableEntry.lock(iorb);
        if (locked != SUCCESS){
            return FAILURE;
        }

        OpenFile openFile = iorb.getOpenFile();
        openFile.incrementIORBCount();
        ThreadCB threadCB = iorb.getThread();

        //setting cylinder, a track consist of blocks, which contains sectors.
        int cylinder_index = getCylinderNumber(iorb.getBlockNumber(), (Disk)this);
        iorb.setCylinder(cylinder_index);


        if (threadCB.getStatus() == ThreadKill /*22*/){
            return FAILURE;
        }


        if (!this.isBusy()){ // when disk is idle
            this.startIO(iorb);
            lastCylinder = iorb.getCylinder();
            return SUCCESS;
        }

        //If the device is busy
        if (threadCB.getStatus() != ThreadKill) {
           // ((GenericList) queueList).append(iorb);
            IORBQueue currentOpenIORBQueue = null;
            //looking for open queue.
            for (int i=0; i< queueList.size(); i++){
                //IORBQueue temp = (IORBQueue) ((GenericList)queueList).getAt(i);
                IORBQueue temp = queueList.get(i);
                if (temp.isOpen()){
                    currentOpenIORBQueue = temp;
                    currentOpenQueueIndex=i;
                    break;
                }
            }
            if (currentOpenIORBQueue == null){
                currentOpenIORBQueue = new IORBQueue();
                queueList.add(currentOpenIORBQueue);
                //((GenericList)queueList).append(currentOpenIORBQueue);
                currentOpenQueueIndex=queueList.size()-1;//last queue would be current open queue.
            }

            currentOpenIORBQueue.append(iorb);

            //keep track of iorb for each thread.
            if (iorb_by_thread_list.get(iorb.getThread())==null){
                Vector<IORB> vector = new Vector<>();
                vector.addElement(iorb);
                iorb_by_thread_list.put(iorb.getThread(), vector);
            }else {
                iorb_by_thread_list.get(iorb.getThread()).addElement(iorb);
            }

            //keep track of iorbQueue
            ((GenericList)iorbQueue).append(iorb);

        }

        return SUCCESS;

    }

    /**
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB()
    {
        // your code goes here
        //picking the current using queue.
        //IORBQueue currentUsingQueue = null;

//        for (int i=0; i< queueList.size(); i++){
//            IORBQueue temp = queueList.get(i);
//            if (!temp.isEmpty()){
//                currentUsingQueue = temp;
//                currentUsingQueueIndex = i;
//                break;
//            }
//        }

//        if (currentUsingQueue == null){
//            return null;
//        }
        MyOut.print(queueList, "The queueList is "+queueList);

        if (currentUsingQueueIndex >= queueList.size()){
            int count = -1;
            for (int i=0; i<queueList.size(); i++){
                if (!queueList.get(i).isEmpty()){
                    currentUsingQueueIndex = i;
                    count = i;
                    break;
                }
            }

            if (count == -1){
                //all queues are empty
                return null;
            }
        }

        IORBQueue currentUsingQueue = queueList.get(currentUsingQueueIndex);

        if (currentUsingQueue.isEmpty()){
            currentUsingQueue.open();
            //currentUsingQueueIndex++;

                int count = -1;
                for (int i=0; i<queueList.size(); i++){
                    if (!queueList.get(i).isEmpty()){
                        currentUsingQueueIndex = i;
                        currentUsingQueue = queueList.get(currentUsingQueueIndex);
                        count = i;
                        break;
                    }
                }
                MyOut.print(count, "count is "+count);
                if (count == -1){
                    //all queues are empty
                    return null;
                }

        }


        //MyOut.print(queueList, "The queueList is "+queueList);
        MyOut.print(currentUsingQueue, "The currentUsingQueue is "+currentUsingQueue);

        //getting IORB from current
        int selected = 0;
        for (int i=0; i<currentUsingQueue.length(); i++){
            IORB current = (IORB) currentUsingQueue.getAt(i);
            IORB slectedIORB = (IORB) currentUsingQueue.getAt(selected);
            //(current.getCylinder()-lastCylinder)*Disk.getSeekTimePerCylinder gives the seek time, use math, seekTimePercylinder can be ignored when
            //we only care who's smaller.
            if (Math.abs(current.getCylinder()-lastCylinder) < Math.abs(slectedIORB.getCylinder()-lastCylinder)){
                selected = i;
            }

        }


        IORB iorb = (IORB) currentUsingQueue.getAt(selected);
        currentUsingQueue.remove(iorb);

        ((GenericList) iorbQueue).remove(iorb);//keep track of iorbQueue.

        MyOut.print(iorb, "The IORB is "+iorb);

        lastCylinder = iorb.getCylinder();
        currentUsingQueue.close();

        if (currentUsingQueue.isEmpty()){
            queueList.remove(currentUsingQueue);
        }


        //currentOpenQueueIndex++;
        if (currentOpenQueueIndex>=queueList.size() || !queueList.get(currentOpenQueueIndex).isOpen()){
            int count = -1;
            for (int i=0; i<queueList.size(); i++){
                if (queueList.get(i).isOpen()){
                    currentOpenQueueIndex = i;
                    count = i;
                    break;
                }
            }

            if (count==-1){
                queueList.add(new IORBQueue());
                currentOpenQueueIndex = queueList.size()-1;
            }
        }


        //remove the iorb from list
        try{
            iorb_by_thread_list.get(iorb.getThread()).remove(iorb);
        } catch (NullPointerException e){
            MyOut.print(iorb_by_thread_list, "Shouldn't reach here.");
        }



        return iorb;

    }

    /**
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
    public void do_cancelPendingIO(ThreadCB thread)
    {
        // your code goes here
        MyOut.print(queueList, "do_cancelPendingIO: QueueList is "+queueList);

        int count;
        for (count=0; count<queueList.size(); count++){
            if (!queueList.get(count).isEmpty()){
                break;
            }
        }

        if (count==queueList.size()){ // all queues in queuelist is empty.
            return;
        }

        if (thread.getStatus() != ThreadKill){
            return;
        }

//        Vector<IORB> vector = iorb_by_thread_list.get(thread);


//        for (int i=0; i<vector.size(); i++){
//            IORB iorb = vector.get(i);
//
//            iorb.getPage().unlock();
//            iorb.getOpenFile().decrementIORBCount();
//            if(iorb.getOpenFile().getIORBCount() == 0 && iorb.getOpenFile().closePending) {
//                iorb.getOpenFile().close();
//            }
//
//            //remove IORB from all queues.
//            for (int j=0; i<queueList.size(); j++){
//                if (queueList.get(j) != null && !queueList.get(j).isEmpty() && queueList.get(j).contains(iorb)){
//                    queueList.get(i).remove(iorb);
//                }
//            }
//
//        }

        for (int i=0; i<queueList.size(); i++){


            IORBQueue queue = queueList.get(i);
            MyOut.print(queue, "Reaching "+i+"th, queue is "+queue);
            if (!queue.isEmpty()){

                for (int j=0; j<queue.length(); j++){
                    IORB iorb = (IORB) queue.getAt(j);
                    if (iorb != null && iorb.getThread().getID() == thread.getID()){

                        ((GenericList)iorbQueue).remove(iorb);

                        MyOut.print(iorb, "IORB to be removed: "+iorb);
                        iorb.getPage().unlock();
                        iorb.getOpenFile().decrementIORBCount();
                        if(iorb.getOpenFile().getIORBCount() == 0 && iorb.getOpenFile().closePending) {
                            iorb.getOpenFile().close();
                        }

                        queue.remove(iorb);
                    }
                }
            }
        }

        MyOut.print(queueList, "after do_cancelPendingIO: QueueList is "+queueList);


    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atWarning()
    {
        // your code goes here

    }

    public int getCylinderNumber(int blockNumber, Disk disk){
        int block_size = (int) Math.pow(2, MMU.getVirtualAddressBits() - MMU.getPageAddressBits());//also page_size
        int sector_size = disk.getBytesPerSector();
        int sectors_per_track = disk.getSectorsPerTrack();
        int track_size = sector_size*sectors_per_track;
        int blocks_per_track = track_size/block_size;
        int blocks_per_cylinder = blocks_per_track*disk.getPlatters();
        //getPlatters returns the number of platters in the disk, = the number of tracks in a cylinder in OSP 2(one side).
        return blockNumber/blocks_per_cylinder;
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */



}

/*
      Feel free to add local classes to improve the readability of your code
*/
class IORBQueue extends GenericList{
    private boolean open;
    IORBQueue(){
        super();
        open = true;
    }

    public void close(){
        open = false;
    }

    public void open(){
        open = true;
    }

    public boolean isOpen(){
        return open;
    }
}


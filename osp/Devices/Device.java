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
    private static int lastCylinder;

    public Device(int id, int numberOfBlocks)
    {
        // your code goes here
        super(id,numberOfBlocks);
        //this.iorbQueue = new GenericList();
        currentOpenQueueIndex =0;
        currentUsingQueueIndex =0;

        queueList = new ArrayList<>(4);
        for (int i=0; i<4; i++){
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
           // ((GenericList) iorbQueue).append(iorb);
            IORBQueue currentOpenIORBQueue = null;
            //looking for open queue.
            for (int i=currentOpenQueueIndex; i< queueList.size(); i++){
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
                currentOpenQueueIndex++;
            }

            currentOpenIORBQueue.append(iorb);

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
        IORBQueue currentUsingQueue = null;

        for (int i=currentUsingQueueIndex; i< queueList.size(); i++){
            IORBQueue temp = queueList.get(i);
            if (!temp.isEmpty()){
                currentUsingQueue = temp;
                currentUsingQueueIndex = i;
                break;
            }
        }

        if (currentUsingQueue == null){
            return null;
        }

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

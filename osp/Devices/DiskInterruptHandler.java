package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/**
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /** 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
        // your code goes here
        IORB iorb = (IORB) InterruptVector.getEvent();
        ThreadCB threadCB = iorb.getThread();
        TaskCB taskCB = threadCB.getTask();
        OpenFile openFile = iorb.getOpenFile();
        PageTableEntry pageTableEntry = iorb.getPage();
        FrameTableEntry frameTableEntry = pageTableEntry.getFrame();

        openFile.decrementIORBCount();

        if (openFile.closePending && openFile.getIORBCount()==0){
            openFile.close();
        }

        pageTableEntry.unlock();

//        if (taskCB.getStatus() == TaskLive){
//            if (iorb.getDeviceID() != SwapDeviceID && threadCB.getStatus() != ThreadKill){
//                frameTableEntry.setReferenced(true);
//                if (iorb.getIOType() == FileRead){
//                    frameTableEntry.setDirty(true);
//                }
//            }else {
//                frameTableEntry.setDirty(false);
//            }
//        }

        if (threadCB.getStatus() != ThreadKill && iorb.getDeviceID() != SwapDeviceID){
            frameTableEntry.setReferenced(true);
        }

        if (iorb.getDeviceID()!= SwapDeviceID && iorb.getIOType() == FileRead && taskCB.getStatus()!= TaskTerm){
            frameTableEntry.setDirty(true);
        }

        if (iorb.getDeviceID()== SwapDeviceID &&  taskCB.getStatus()!= TaskTerm){
            frameTableEntry.setDirty(false);
        }

        if (taskCB.getStatus() == TaskTerm){//if task terminated
            try {
                if (frameTableEntry.getReserved() == taskCB){
                    frameTableEntry.setUnreserved(taskCB);
                }
            }catch (NullPointerException e){
                //do nothing.
            }
        }

        iorb.notifyThreads();
        Device.get(iorb.getDeviceID()).setBusy(false);
        IORB iorb1 = Device.get(iorb.getDeviceID()).dequeueIORB();

        if (iorb1 != null){
            Device.get(iorb.getDeviceID()).startIO(iorb1);
        }

        ThreadCB.dispatch();
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/

/** ----------------------- FileSystem.java --------------------------
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
 * @author Camila Valdebenito
// *
// * DESCRIPTION:
// * This class
// *
// *
// * ASSUMPTIONS: Assumes that the user has access to ThreadOS
// */

public class FileSystem {
   private SuperBlock superblock;
   private Directory directory;
   private FileTable filetable;

   public FileSystem( int diskBlock ) {
      // create superblock and format disk with 64 inodes in default
      superblock = new SuperBlock( diskBlocks );

      // creat directory and register "/" in directory entry 0
      directory = new Directory( superblock.inodeBlocks );

      // file table is created and stores directory in the file table
      filetable = new FileTable( directory );

      // directory reconstruction
      FileTableEntry dirEnt = open( "/", "r" );
      int directorySize = fsize( dirEnt );
      if( dirSize > 0) {
         byte[] dirData = new byte[directorySize];
         read( dirEnt, dirData );
         directory.bytes2directory( dirData );
      }
      close( dirEnt );
   }

//------------------------sync()-----------------------
//This syncs the directory to the physical disk and then
//syncs the superblock
   void sync(){
     //Temp gets the directory information
     byte[] temp = directory.directory2bytes();

     //root is from the root of the directory that we open
     FileTableEntry root = open("/", "w");

     //writes to root
     write(root, directory.directory2bytes);

     //closes root
     close(root);

     //This syncs the superblock
     temp = new byte[superBlock.diskSize];
     SysLib.int2bytes(freeList, temp, 8);
     SysLib.int2bytes(totalBlocks, temp, 0);
     SysLib.int2bytes(totalInodes, temp, 4);
     SysLib.rawwrite(0, temp);
   }

   //--------------------format()---------------------------
   //@params files:this will represent the amount of Inodes present
   //@returns true if complete
   //This formats the firectory, superblock, and filetable
   boolean format(int files){
     //format the superblock
     superblock.format(files);

     //formats directory based on totalInodes
     directory = new Directory(superBlock.totalInodes);

     //formats filetable based on directory
     filetable = new FileTable(directory);
     return true;
   }

   //---------------------open(String filename, String mode)
   //@params filename and mode: filename is the name of the file you want to open
   //mode is what mode you want to have while opening the file
   //@returns null if you want to write to a file and the pointer associated is messed up
   //returns true if successfully opens the file
   FileTableEntry open(String filename, String mode){
     //creates a new FileTableEntry based on allocating the new file that is being opened
      FileTableEntry ftEnt = filetable.falloc(filename, mode);

      //if mode is write and it has a null pointer it returns null
      if(mode == "w" && ftEnt.inode.count != 1)
        return null;

      //returns the FileTableEntry
      return ftEnt;
   }

   //---------------------close(FileTableEntry ftEnt)-------------------
   //@params ftEnt: FileTableEntry that you want to close
   //@returns true or false based on if a FileTableEntry was closed
   //This is to close a FileTableEntry
   boolean close(FileTableEntry ftEnt){
     //if the ftEnt is null we don't wanna break the system and return false
     if(ftEnt == null)
      return false;
     else{
       //Does synchronized so multiple values don't overlap
       synchronized(ftEnt){

         //lowers count by one
         ftEnt.count -= 1;

         //if FileTableEntry count is 0 that means we free it from the file table and no matter what return true
         if(ftEnt.count == 0)
          return filetable.ffree(ftEnt);
         return true;
       }
     }
   }

   //----------------------fsize(FileTableEntry ftEnt)---------------------
   //@params ftEnt: this the the FileTableEntry it will find the size of
   //@returns length of FileTableEntry
   int fsize(FileTableEntry ftEnt){
     //synchronized so that threads don't screw up the FileTableEntry
     synchronized(ftEnt){
       return ftEnt.inode.length;
     }
   }

   int read(FileTableEntry ftEnt, byte[] buffer){

   }

   int write(FileTableEntry ftEnt, byte[] buffer){

   }

   private boolean deallocateAllBlocks(FileTableEntry ftEnt)
   {


   }

   // deletes the file specified by given fileName.
   // If the file is currently open, it is not destroyed
   // until the last open on it is closed, but new attempts to open it will fail.
   public boolean delete(String filename)
   {
      FileTableEntry tcb = open(filename, "w"); // Grabs the iNode(aka tcb)

      if(directory.ifree(tcb.iNumber) && close(tcb)) // frees iNode and closes successfully
      {
         return true;   // deletion successful
      }

      return false;     // deletion unsuccessful
   }

   private final int SEEK_SET = 0;
   private final int SEEK_CUR = 1;
   private final int SEEK_END = 2;

    // Updates the seek pointer corresponding to fd as follows:
    // If whence is SEEK_SET (= 0), the file's seek pointer is set to offset bytes from the beginning of the file.
    // If whence is SEEK_CUR (= 1), the file's seek pointer is set to its current value plus the offset. The offset can be positive or negative.
    // If whence is SEEK_END (= 2), the file's seek pointer is set to the size of the file plus the offset. The offset can be positive or negative.
   public synchronized int seek(FileTableEntry ftEnt, int offset, int whence){

      switch(whence)
      {
         case SEEK_SET:
            ftEnt.seekPtr = offset;

         case SEEK_CUR:
            ftEnt.seekPtr += offset;

         case SEEK_END:
            ftEnt.seekPtr = offset + fsize[ftEnt];

         default:
            return -1;
      }

      if(ftEnt.seekPtr < 0)
      {
         ftEnt.seekPtr = 0;
      }
      else if (ftEnt.seekPtr > fsize[ftEnt])
      {
         ftEnt.seekPtr = fsize[ftEnt];

      }

      return ftEnt.seekPtr;

   }

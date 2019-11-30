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

   boolean format(int files){
     
   }

   FileTableEntry open(String filename, String mode){

   }

   boolean close(FileTableEntry ftEnt){

   }

   int fsize(FileTableEntry ftEnt){

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

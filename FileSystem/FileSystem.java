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
      superblock = new SuperBlock( diskBlock );

      // creat directory and register "/" in directory entry 0
      directory = new Directory( superblock.totalInodes );

      // file table is created and stores directory in the file table
      filetable = new FileTable( directory );

      // directory reconstruction
      FileTableEntry dirEnt = open( "/", "r" );
      int directorySize = fsize( dirEnt );
      if( directorySize > 0) {
         byte[] dirData = new byte[directorySize];
         read( dirEnt, dirData );
         directory.bytes2directory( dirData );
      }
      close( dirEnt );
   }

//------------------------sync()-----------------------
//This syncs the directory to the physical disk and then
//syncs the superblock
   public void sync(){
     //Temp gets the directory information
      byte[] temp = directory.directory2bytes();

     //root is from the root of the directory that we open
      FileTableEntry root = open("/", "w");

     //writes to root
      write(root, directory.directory2bytes());

     //closes root
      close(root);

     //This syncs the superblock
      temp = new byte[superblock.diskSize];
      SysLib.int2bytes(superblock.freeList, temp, 8);
      SysLib.int2bytes(superblock.totalBlocks, temp, 0);
      SysLib.int2bytes(superblock.totalInodes, temp, 4);
      SysLib.rawwrite(0, temp);
   }

   //--------------------format()---------------------------
   //@params files:this will represent the amount of Inodes present
   //@returns true if complete
   //This formats the firectory, superblock, and filetable
   public boolean format(int files){
     //format the superblock
      superblock.format(files);

     //formats directory based on totalInodes
      directory = new Directory(superblock.totalInodes);

     //formats filetable based on directory
      filetable = new FileTable(directory);
      return true;
   }

   //---------------------open(String filename, String mode)
   //@params filename and mode: filename is the name of the file you want to open
   //mode is what mode you want to have while opening the file
   //@returns null if you want to write to a file and the pointer associated is messed up
   //returns true if successfully opens the file
   public FileTableEntry open(String filename, String mode){
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
   public boolean close(FileTableEntry ftEnt){
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
   public int fsize(FileTableEntry ftEnt){
     //if it is null return -1
      if(ftEnt == null)
         return -1;

     //synchronized so that threads don't screw up the FileTableEntry
      synchronized(ftEnt){
         return ftEnt.inode.length;
      }
   }





   public int write(FileTableEntry ftEnt, byte[] buffer){
     int blockSize = 512;
     int size = buffer.length;
     int bytesWritten = 0;
     int bytesLeft = 0;
     int fileLength = fsize(ftEnt);

     if(ftEnt.mode == "a" || ftEnt.mode == "r" || buffer == null)
      return -1;

      while(size > 0){
        int tgtBlock = ftEnt.inode.getBlockIndex(ftEnt.seekPtr);
        if(tgtBlock == -1){
          if(ftEnt.inode.indirect < 0){
            return -1;
          }
          tgtBlock = superblock.nextBlock();
        }
        byte[] temp = new byte[blockSize];
        SysLib.rawread(tgtBlock, temp);
        int ptr = ftEnt.seekPtr % blockSize;
        int diff = blockSize - ptr;

        if(diff > size){
          System.arraycopy(buffer, bytesWritten, temp, ptr, size);
          SysLib.rawwrite(tgtBlock, temp);

          ftEnt.seekPtr = ftEnt.seekPtr + size;
          bytesWritten = bytesWritten + size;
          size = 0;
        }
        else{
          System.arraycopy(buffer, bytesWritten, temp, ptr, diff);
          SysLib.rawwrite(tgtBlock, temp);

          ftEnt.seekPtr = ftEnt.seekPtr + diff;
          bytesWritten = bytesWritten + diff;
          size = size - diff;
        }
        if(ftEnt.seekPtr > ftEnt.inode.length)
          ftEnt.inode.length = ftEnt.seekPtr;
      }
      ftEnt.inode.toDisk(ftEnt.iNumber);
      return bytesWritten;
   }





   // ---------------------- read(FileTableEntry ftEnt, byte[] buffer) --------------------------
   // reads up to buffer.length bytes from the file indicated by ftEnt, starting at the position currently
   // pointed to by the seek pointer. If bytes remaining between the current seek pointer and the end of
   // file are less than buffer.length, SysLib.read reads as many bytes as possible,putting them into the
   // beginning of buffer. It increments the seek pointer by the number of bytes to have been read.
   // The return value is the number of bytes that have been read, or a negative value upon an error.
   public synchronized int read(FileTableEntry ftEnt, byte[] buffer)
   {
      int blockSize = 512;
      int size = buffer.length;
      int fileLength = fsize(ftEnt);
      int bytesLeft = 0;
      int bytesRead = 0;

      // checks for reading errors
      if(ftEnt.mode == "a"|| ftEnt.mode == "w" || buffer == null)
      {
         return -1;
      }

      while(ftEnt.seekPtr < fileLength && size > 0)
      {
         // retrieves the block number
         int blockNum = ftEnt.inode.getBlockIndex(ftEnt.seekPtr);

         if(blockNum == -1) // checks for invalid blockNum/ block location
         {
            break;
         }
         byte[] data = new byte[blockSize];
         SysLib.rawread(blockNum, data);

         int dataOffset = ftEnt.seekPtr % blockSize;
         int remainingFile = fileLength - ftEnt.seekPtr;
         int remainingBlocks = blockSize - dataOffset;

         if(remainingFile > remainingBlocks)
         {
            bytesLeft = remainingBlocks;

         }
         else
         {
            bytesLeft = remainingFile;
         }

         bytesLeft = Math.min(bytesLeft, remainingFile);
         System.arraycopy(data, dataOffset, buffer, bytesRead, bytesLeft);

         bytesRead += bytesLeft;           // update data read
         ftEnt.seekPtr += bytesLeft;       // update pointer to account for data read
         size -= bytesLeft;



      }

      return bytesRead;  // number of bytes read



   }


   //---------------------- boolean delete(String filename) ---------------------
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

   //---------------------- seek(FileTableEntry ftEnt, int offset, int whence) ---------------------
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
         // file's seek pointer is set to offset bytes from the beginning of the file
         case SEEK_SET:
            ftEnt.seekPtr = offset;
            break;

         // file's seek pointer is set to its current value plus the offset
         case SEEK_CUR:
            ftEnt.seekPtr += offset;
            break;

         // file's seek pointer is set to the size of the file plus the offset
         case SEEK_END:
            ftEnt.seekPtr = offset + fsize(ftEnt);
            break;

         default:
            return -1;
      }

      if(ftEnt.seekPtr < 0)
      {
         ftEnt.seekPtr = 0;
      }
      else if (ftEnt.seekPtr > fsize(ftEnt))
      {
         ftEnt.seekPtr = fsize(ftEnt);

      }

      return ftEnt.seekPtr;

   }
}

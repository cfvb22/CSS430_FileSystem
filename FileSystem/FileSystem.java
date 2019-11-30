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
  



}

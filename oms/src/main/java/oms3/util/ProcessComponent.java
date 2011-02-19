/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package oms3.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import oms3.annotations.*;

/** Generic Process component.
 *
 * @author od
 */
public class ProcessComponent {

    // the executable file
    @In public String exe;
    
    @In public String[] args;

    @In public String stdin;

    @In public String working_dir;
    @In public boolean verbose = false;

    @Out public String stdout;
    @Out public String stderr;
    @Out public int exitValue;

    @Execute
    public void execute() {
        Processes p = new Processes(new File(exe));
        p.setArguments((Object[]) args);
        p.setVerbose(verbose);

        try {
            if (stdin != null && !stdin.isEmpty()) {
                p.redirectInput(new FileInputStream(stdin));
            }
            if (working_dir != null && !working_dir.isEmpty()) {
                p.setWorkingDirectory(new File(working_dir));
            }

            final StringBuffer out_buff = new StringBuffer();
            final StringBuffer err_buff = new StringBuffer();
            p.redirectOutput(new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    out_buff.append((char) b);
                }
            });

            p.redirectError(new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    err_buff.append((char) b);
                }
            });

            exitValue = p.exec();
            stdout = out_buff.toString();
            stderr = err_buff.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

using System;
using System.IO;
using System.IO.Compression;
using System.Diagnostics;
using System.Reflection;
using System.Windows.Forms;

namespace StarkMouseLauncher
{
    class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            string tempDir = Path.Combine(Path.GetTempPath(), "StarkMouse_Temp_" + Guid.NewGuid().ToString().Substring(0,8));
            Directory.CreateDirectory(tempDir);
            
            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("StarkMouse_Release.zip"))
                {
                    if (stream == null) {
                        MessageBox.Show("Could not find embedded payload!", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        return;
                    }
                    string zipPath = Path.Combine(tempDir, "release.zip");
                    using (FileStream fs = new FileStream(zipPath, FileMode.Create))
                    {
                        stream.CopyTo(fs);
                    }
                    ZipFile.ExtractToDirectory(zipPath, tempDir);
                }
                
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = "java";
                psi.Arguments = "-jar StarkMouse.jar";
                psi.WorkingDirectory = tempDir;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                
                using (Process p = Process.Start(psi))
                {
                    p.WaitForExit();
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Failed to launch Stark Mouse. Make sure Java is installed and in your PATH.\n\nError details: " + ex.Message, "Stark Mouse Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                try { Directory.Delete(tempDir, true); } catch { }
            }
        }
    }
}

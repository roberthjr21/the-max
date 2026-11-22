package co.themax.fixture;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class SendChat extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String chat = "22/11/26, 09:00 - Ana: Buenos días\n22/11/26, 09:02 - Sol: ¡Hola!\n22/11/26, 09:08 - Ana: Me encanta el café";
        try (FileOutputStream out = openFileOutput("chat.txt", MODE_PRIVATE)) {
            out.write(chat.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
        Uri uri = Uri.parse("content://co.themax.fixture.chat/chat.txt");
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
            .setClassName("co.themax.chat","co.themax.chat.MainActivity")
            .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newRawUri("chat",uri));
        startActivity(share);
        finish();
    }
}

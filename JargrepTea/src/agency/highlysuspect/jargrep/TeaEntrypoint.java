package agency.highlysuspect.jargrep;

import org.teavm.interop.Async;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.file.File;
import org.teavm.jso.file.FileList;
import org.teavm.jso.typedarrays.Int8Array;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;

public class TeaEntrypoint {
	public static final HTMLDocument document = HTMLDocument.current();
	
	public static void main(String[] args) {
		System.out.println("HELLO WORODL");
		
		document.getBody().clear();
		
		HTMLElement form = document.createElement("form");
		
		HTMLElement fieldset = document.createElement("fieldset")
			.withChild(document.createElement("legend").withText("lets go"));
		
		HTMLElement fileInput = document.createElement("input")
			.withAttr("type", "file")
			.withAttr("multiple", "true");
		HTMLElement search = document.createElement("input")
			.withAttr("type", "text");
		HTMLElement submit = document.createElement("button")
			.withText("Search!");
		
		submit.addEventListener("click", evt -> {
			evt.preventDefault();
			onSubmit(fileInput, search);
		});
		
		fieldset.withChild(fileInput).withChild(search).withChild(submit);
		form.withChild(fieldset);
		document.getBody().appendChild(form);
	}
	
	//It feels like there should be a better way to do this, LOL.
	static byte[] fromInt8Array(Int8Array array) {
		byte[] bytes = new byte[array.getByteLength()];
		for(int i = 0; i < bytes.length; i++) {
			bytes[i] = array.get(i);
		}
		return bytes;
	}
	
	@Async
	public static void onSubmit(HTMLElement fileInput, HTMLElement search) {
		FileList fileList = ((HTMLInputElement) fileInput).getFiles();
		
		Writer.FsWriter writer = new ConsoleWriter(System.out);
		SearchOpts opts = new SearchOpts();
		opts.grep = Pattern.compile(((HTMLInputElement) search).getValue());
		
		JarGrep jg = new JarGrep(opts);
		
		for(int i = 0; i < fileList.getLength(); i++) {
			File file = fileList.item(i);
			
			file.arrayBuffer().then(arrayBuffer -> {
				try {
					byte[] bytes = fromInt8Array(new Int8Array(arrayBuffer));
					ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
					
					jg.visitInputStream(writer, file.getName(), bais);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				
				return 0;
			});
		}
	}
}

package agency.highlysuspect.jargrep;

import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLImageElement;

import java.util.function.Consumer;

import static agency.highlysuspect.jargrep.Writer.*;

public class HtmlWriter implements FsWriter, TxtWriter, BinWriter, ClsWriter, FldWriter, MthWriter {
	//root writer
	public HtmlWriter(HTMLDocument document, HTMLElement container) {
		this.document = document;
		this.element = document.createElement("div"); //todo better
		this.element.getClassList().add("root");
		this.appender = container::appendChild;
		this.parent = null;
	}
	
	//child writer
	protected HtmlWriter(HtmlWriter parent, Icon icon, String header) {
		this.document = parent.document;
		this.element = createComplexElement(icon, header);
		this.parent = parent;
		
		//hmm
		HTMLElement ul = document.createElement("ul");
		this.element.appendChild(ul);
		
		//hmmMMMMMMM
		HTMLElement parentList = parent.element.querySelector("ul");
		if(parentList == null) this.appender = parent.element::appendChild;
		else this.appender = e -> {
			HTMLElement li = document.createElement("li");
			li.appendChild(e);
			parentList.appendChild(li);
		};
	}
	
	protected final HTMLDocument document;
	protected final HTMLElement element;
	protected final Consumer<HTMLElement> appender;
	protected final HtmlWriter parent;
	protected boolean appended = false;
	
	protected void ensureAppended() {
		if(!appended) {
			if(parent != null) parent.ensureAppended();
			appender.accept(element);
			appended = true;
		}
	}
	
	protected void append(Icon icon, String s) {
		ensureAppended();
		element.appendChild(createSimpleElement(icon, s));
	}
	
	protected HTMLElement createComplexElement(Icon icon, String header) {
		HTMLElement details = document.createElement("details");
		details.setAttribute("open", "true");
		
		HTMLElement summary = document.createElement("summary");
		summary.appendChild(icon.createElement(document));
		summary.appendChild(document.createTextNode(header));
		details.appendChild(summary);
		
		return details;
	}
	
	protected HTMLElement createSimpleElement(Icon icon, String message) {
		HTMLElement p = document.createElement("p");
		p.appendChild(icon.createElement(document));
		p.appendChild(document.createTextNode(message));
		return p;
	}
	
	protected enum Icon {
		ARCHIVE, TEXT, BIN, CLAZZ,
		DIRECTORY,
		TEXT_MATCH, BIN_MATCH,
		FIELD, METHOD,
		FIELD_VALUE, CONSTANT,
		FIELD_ACCESS, METHOD_ACCESS;
		
		HTMLElement createElement(HTMLDocument document) {
			//TODO
			HTMLImageElement icon = (HTMLImageElement) document.createElement("img");
			icon.setAlt(name());
			icon.setWidth(20);
			icon.setHeight(20);
			return icon;
		}
	}
	
	@Override
	public FsWriter archiveWriter(String filename) {
		return new HtmlWriter(this, Icon.ARCHIVE, filename);
	}
	
	@Override
	public TxtWriter textWriter(String filename) {
		return new HtmlWriter(this, Icon.TEXT, filename);
	}
	
	@Override
	public BinWriter binaryWriter(String filename) {
		return new HtmlWriter(this, Icon.BIN, filename);
	}
	
	@Override
	public ClsWriter classWriter(String filename) {
		return new HtmlWriter(this, Icon.CLAZZ, filename);
	}
	
	@Override
	public void writeFileName(String filename) {
		ensureAppended();
	}
	
	@Override
	public void writeDirectoryName(String dirName) {
		append(Icon.DIRECTORY, dirName);
	}
	
	@Override
	public void writeTextMatch(String match) {
		append(Icon.TEXT_MATCH, match);
	}
	
	@Override
	public void writeBinaryMatch() {
		append(Icon.BIN_MATCH, "(binary file matches)");
	}
	
	@Override
	public void writeClassName(String className) {
		ensureAppended();
	}
	
	@Override
	public FldWriter fieldWriter(String fieldName) {
		return new HtmlWriter(this, Icon.FIELD, fieldName);
	}
	
	@Override
	public MthWriter methodWriter(String methodName) {
		return new HtmlWriter(this, Icon.METHOD, methodName);
	}
	
	@Override
	public void writeFieldName(String name) {
		ensureAppended();
	}
	
	@Override
	public void writeFieldValue(String name, String value) {
		append(Icon.FIELD_VALUE, value);
	}
	
	@Override
	public void writeMethodName(String name) {
		ensureAppended();
	}
	
	@Override
	public void writeConstant(String name, String constant) {
		append(Icon.CONSTANT, constant);
	}
	
	@Override
	public void writeFieldAccess(String name, FieldAccessType type, String fieldOwner, String fieldName, String fieldDesc) {
		append(Icon.FIELD_ACCESS, type.name() + ": " + fieldOwner + "#" + fieldName + " " + fieldDesc);
	}
	
	@Override
	public void writeMethodAccess(String name, MethodCallType type, String methodOwner, String methodName, String methodDesc) {
		append(Icon.METHOD_ACCESS, type.name() + ": " + methodOwner + "#" + methodName + " " + methodDesc);
	}
	
	@Override
	public void writeEnd() {
	
	}
}

package com.zayrex.quicknote;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class QuicknoteApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuicknoteApplication.class, args);
	}

}

@Document(collection = "notes")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
class Note {
	@Id
	private String id;
	private String textContent;

	@Override
	public String toString() {
		return textContent;
	}
}
interface NotesRepository extends MongoRepository<Note, String> {

}

@Controller
class QuicknoteController {

	@Autowired
	private NotesRepository notesRepository;
	@Autowired
	private QuicknoteProperties properties;
	private Parser parser = Parser.builder().build();
	private HtmlRenderer renderer = HtmlRenderer.builder().build();

	@GetMapping("/")
	public String index(Model model) {
		getAllNotes(model);
		return "index";
	}

	private void getAllNotes(Model model) {
		List<Note> notes = notesRepository.findAll();
		Collections.reverse(notes);
		model.addAttribute("notes", notes);
	}
	private void saveNote(String textContent, Model model) {
		if (textContent != null && !textContent.trim().isEmpty()) {
			Node document = parser.parse(textContent.trim());
			String html = renderer.render(document);
			notesRepository.save(new Note(null, html));
			//clear text field
			model.addAttribute("textContent", "");
		}
	}
	@PostMapping("/note")
	public String saveNotes(@RequestParam("image") MultipartFile file,
							@RequestParam String textContent,
							@RequestParam(required = false) String save,
							@RequestParam(required = false) String upload,
							Model model) throws Exception {

		if (save != null && save.equals("Save")) {
			saveNote(textContent, model);
			getAllNotes(model);
			return "redirect:/";
		}

		if (upload != null && upload.equals("Upload")) {
			if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isEmpty()) {
				uploadImage(file, textContent, model);
			}
			getAllNotes(model);
			return "index";
		}
		return "index";
	}
	private void uploadImage(MultipartFile file, String textContent, Model model) throws Exception {
		File uploadsDir = new File(properties.getUploadDir());
		if (!uploadsDir.exists()) {
			uploadsDir.mkdir();
		}
		String fileId = UUID.randomUUID().toString() + "."
				+ file.getOriginalFilename().split("\\.")[1];
		file.transferTo(new File(properties.getUploadDir() + fileId));
		model.addAttribute("textContent", textContent + " ![](/uploads/" + fileId + ")");
	}

}
@ConfigurationProperties(prefix = "quicknote")
class QuicknoteProperties {
	@Value("${uploadDir:/tmp/uploads/}")
	private String uploadDir;

	public String getUploadDir() {
		return uploadDir;
	}
}

@Configuration
@EnableConfigurationProperties(QuicknoteProperties.class)
class QuicknoteConfig implements WebMvcConfigurer {

	@Autowired
	private QuicknoteProperties properties;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry
				.addResourceHandler("/uploads/**")
				.addResourceLocations("file:" + properties.getUploadDir())
				.setCachePeriod(3600)
				.resourceChain(true)
				.addResolver(new PathResourceResolver());
	}

}

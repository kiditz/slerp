package org.slerp.generator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.jboss.forge.roaster.model.util.Strings;
import org.slerp.core.ConcurentDto;
import org.slerp.utils.EntityUtils;
import org.slerp.utils.JpaParser;
import org.springframework.util.StringUtils;

public class ApiGenerator {
	private String srcDir;
	private String apiDir;
	private String packageController;
	private String packageService;
	private String packageEntity;
	private String packageRepository;
	List<JpaParser> parsers = new ArrayList<>();
	private File file;

	public ApiGenerator(String apiDir, String srcDir, String packageEntity, String packageRepository,
			String packageService, String packageController) {
		this.srcDir = srcDir;
		this.apiDir = apiDir;
		this.packageService = packageService;
		this.packageRepository = packageRepository;
		this.packageController = packageController;
		this.packageEntity = packageEntity;
	}

	public void parse() throws IOException {
		ConcurentDto scanService = EntityUtils
				.readBusiness(new File(srcDir, packageService.replace(".", "/").concat("/")));
		for (Entry<Object, Object> entry : scanService.entrySet()) {
			JpaParser parser = new JpaParser(new File(entry.getValue().toString()), packageEntity, packageRepository);
			parser.setUseEntity(true);
			parser.parse();
			parsers.add(parser);
		}
	}

	public void generate() throws IOException {
		JavaClassSource cls = Roaster.create(JavaClassSource.class);
		String controllerName = packageService.substring(packageService.lastIndexOf('.') + 1, packageService.length());
		cls.setName(Strings.capitalize(controllerName.concat("Controller"))).setPublic();
		cls.setPackage(packageController);
		cls.addAnnotation("org.springframework.web.bind.annotation.RestController");
		StringBuilder builder = new StringBuilder();
		StringBuffer getBody = new StringBuffer();
		getBody.append("Domain " + controllerName.concat("Domain").concat(" = ").concat("new Dto();\n"));
		for (JpaParser parser : this.getParsers()) {
			ConcurentDto service = parser.getService();
			String fieldName = StringUtils.uncapitalize(service.getString("className"));
			String mode = service.getString("mode");
			if (mode.equals("transaction")) {
				cls.addField().setType("org.slerp.core.business.BusinessTransaction").setName(fieldName)
						.addAnnotation("org.springframework.beans.factory.annotation.Autowired");

			} else {
				cls.addField().setType("org.slerp.core.business.BusinessFunction").setName(fieldName)
						.addAnnotation("org.springframework.beans.factory.annotation.Autowired");
			}

			MethodSource<JavaClassSource> method = cls.addMethod(fieldName.concat("()")).setPublic();
			method.setReturnType(ConcurentDto.class);
			if (mode.equals("transaction")) {
				if (fieldName.startsWith("edit")) {
					method.addAnnotation("org.springframework.web.bind.annotation.PutMapping")
							.setStringValue("/".concat(fieldName));

				} else if (fieldName.startsWith("remove")) {
					method.addAnnotation("org.springframework.web.bind.annotation.DeleteMapping")
							.setStringValue("/".concat(fieldName));
				} else {
					method.addAnnotation("org.springframework.web.bind.annotation.PostMapping")
							.setStringValue("/".concat(fieldName));
				}
				writeTransactionParam(method, service, parser.getFields());
				writeTransactionBody(method, service, builder);
			} else {
				method.addAnnotation("org.springframework.web.bind.annotation.GetMapping")
						.setStringValue("/".concat(fieldName));
				for (ConcurentDto field : parser.getFields()) {
					String simpleType = field.getString("fieldType");
					simpleType = simpleType.substring(simpleType.lastIndexOf('.') + 1, simpleType.length());
					ParameterSource<JavaClassSource> param = method.addParameter(simpleType,
							field.getString("fieldName"));
					param.addAnnotation("org.springframework.web.bind.annotation.RequestParam")
							.setStringValue(field.getString("fieldName"));

					getBody.append(controllerName.concat("Domain")).append(".put(\"")
							.append(field.getString("fieldName")).append("\", ").append(field.getString("fieldName"))
							.append(");\n");

				}
				getBody.append("return ").append(fieldName).append(".handle(").append(controllerName.concat("Domain"))
						.append(");\n");
				method.setBody(getBody.toString());

			}
			method.addAnnotation("org.springframework.web.bind.annotation.ResponseBody");

		}
		// System.err.println(cls.toString());
		file = new File(apiDir, packageController.replace(".", "/").concat("/")
				.concat(Strings.capitalize(controllerName).concat("Controller")).concat(".java"));
		if (!file.getParentFile().isDirectory())
			file.getParentFile().mkdirs();
		FileWriter writer = new FileWriter(file);
		writer.write(cls.toString());
		writer.close();
		System.out.println("Generator successfully created " + cls.getCanonicalName().concat(".java"));
	}

	public File getFile() {
		return file;
	}

	private void writeTransactionParam(MethodSource<JavaClassSource> method, ConcurentDto service,
			Set<ConcurentDto> fields) {
		String paramDto = getSimpleName(service);
		method.addParameter("Domain", paramDto).addAnnotation("org.springframework.web.bind.annotation.RequestBody");
	}

	private void writeTransactionBody(MethodSource<JavaClassSource> method, ConcurentDto service,
			StringBuilder builder) {
		String paramDto = getSimpleName(service);
		String className = Strings.uncapitalize(service.getString("className"));
		builder.append("Domain outputDto = ".concat(className).concat(".handle(" + paramDto + ");\n"));
		builder.append("return ".concat("outputDto").concat(";"));
		method.setBody(builder.toString());
		builder.setLength(0);
	}

	private String getSimpleName(ConcurentDto servieceDto) {
		String paramDto = servieceDto.getString("packageName");
		int index = paramDto.lastIndexOf('.');
		if (index == 0)
			return paramDto;
		return paramDto.substring(paramDto.lastIndexOf('.') + 1, paramDto.length()).concat("Domain");
	}

	public List<JpaParser> getParsers() {
		return parsers;
	}

	public void setParsers(List<JpaParser> parsers) {
		this.parsers = parsers;
	}

	public static void main(String[] args) throws IOException {
		ApiGenerator generator = new ApiGenerator(
				"/home/kiditz/slerp-git/runtime-EclipseApplication/oauth-api/src/main/java/",
				"/home/kiditz/slerp-git/runtime-EclipseApplication/oauth/src/main/java/", "org.slerp.oauth.entity",
				"org.slerp.oauth.repository", "org.slerp.oauth.service.principal", "org.slerp.core.api");
		generator.parse();
		Scanner scanner = new Scanner(System.in);
		for (int i = 0; i < generator.getParsers().size(); i++) {
			JpaParser parser = generator.getParsers().get(i);

			System.out.println(parser.getService().toString());

		}

		scanner.close();
		generator.generate();
	}

}

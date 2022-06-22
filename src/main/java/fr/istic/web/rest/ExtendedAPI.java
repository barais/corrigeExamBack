package fr.istic.web.rest;


import fr.istic.config.JHipsterProperties;
import fr.istic.domain.Course;
import fr.istic.domain.CourseGroup;
import fr.istic.domain.Exam;
import fr.istic.domain.ExamSheet;
import fr.istic.domain.FinalResult;
import fr.istic.domain.Question;
import fr.istic.domain.Student;
import fr.istic.domain.StudentResponse;
import fr.istic.domain.User;
import fr.istic.security.AuthoritiesConstants;
import fr.istic.service.CourseGroupService;
import fr.istic.service.MailService;
import fr.istic.service.StudentService;
import fr.istic.service.customdto.MailResultDTO;
import fr.istic.service.customdto.StudentMassDTO;
import fr.istic.service.customdto.StudentResultDTO;
import fr.istic.service.customdto.SummaryElementDTO;
import fr.istic.service.customdto.SummaryExamDTO;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * REST controller for managing {@link fr.istic.domain.Comments}.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ExtendedAPI {

    @Inject
    CourseGroupService courseGroupService;

    @Inject
    StudentService studentService;
    @Inject
    MailService mailService;


    @Inject
    JHipsterProperties jHipsterProperties;

    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

//    private final Logger log = LoggerFactory.getLogger(ExtendedAPI.class);

    @ConfigProperty(name = "application.name")
    String applicationName;


    private Exam computeFinalNote(long examId){
        Exam ex = Exam.findById(examId);
        List<ExamSheet> sheets = ExamSheet.findExamSheetByScan(ex.scanfile.id).list();
        sheets.forEach(sh -> {
            // Compute Note
            List<StudentResponse> resps = StudentResponse.findStudentResponsesbysheetId(sh.id).list();
            var finalnote = 0;
            for (StudentResponse resp : resps){
                finalnote = finalnote+ (resp.note * 100 /  resp.question.step  );
            }
            final var finalnote1 = finalnote;
            sh.students.forEach(student -> {
                var q = FinalResult.findFinalResultByStudentIdAndExamId(student.id, examId);
                long count = q.count();
                if (count >0){
                    FinalResult r = q.firstResult();
                    r.note = finalnote1;
                    FinalResult.update(r);
                } else {
                    FinalResult r = new FinalResult();
                    r.student = student;
                    r.exam = ex;
                    r.note = finalnote1;
                    FinalResult.persistOrUpdate(r);

                }
            });
        });
        return ex;
    }
    @POST
    @Path("computeNode/{examId}")
    @Transactional
    @RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    public Response computeFinalNote4Exam(@PathParam("examId") long examId, @Context SecurityContext ctx) {

        this.computeFinalNote(examId);
        return Response.ok().build();
    }

    @POST
    @Path("sendResult/{examId}")
    @Transactional
    @RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    public Response sendResultToStudent(MailResultDTO dto, @PathParam("examId") long examId, @Context SecurityContext ctx) {

        Exam ex = this.computeFinalNote(examId);

        List<Student> students = Student.findStudentsbyCourseId(ex.course.id).list();
        students.forEach(student -> {
            long count = FinalResult.findFinalResultByStudentIdAndExamId(student.id, ex.id).count();
            if (count>0){
                FinalResult r = FinalResult.findFinalResultByStudentIdAndExamId(student.id, ex.id).firstResult();
                ExamSheet sheet = ExamSheet.findExamSheetByScanAndStudentId(ex.scanfile.id,student.id).firstResult();
                String uuid = sheet.name;
                String body = dto.getBody();
                body = body.replace("${url}",this.jHipsterProperties.mail.baseUrl + "/copie/" + uuid+ "/1");
                body =body.replace("${firstname}",student.firstname);
                body = body.replace("${lastname}",student.name);
                final DecimalFormat df = new DecimalFormat("0.00");
                body = body.replace("${note}",df.format(r.note / 100));
                mailService.sendEmail(student.mail,  dto.getSubject(), body);
                //  TODO Send EMAIL
                // mailService.sendEmailFromTemplate(user, template, subject)


            }else {
               // TODO Send EMAIL

                // Pas de copie pour cet étudiant
            }
        });


        return Response.ok().build();
    }

    @GET
    @Path("showResult/{examId}")
    @Transactional
    @RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    public Response showResult(@PathParam("examId") long examId, @Context SecurityContext ctx) {
        Exam ex = this.computeFinalNote(examId);
        List<StudentResultDTO> results = new ArrayList<>();
        List<Student> students = Student.findStudentsbyCourseId(ex.course.id).list();
        students.forEach(student -> {
            long count = FinalResult.findFinalResultByStudentIdAndExamId(student.id, ex.id).count();
            if (count>0){
                FinalResult r = FinalResult.findFinalResultByStudentIdAndExamId(student.id, ex.id).firstResult();
                ExamSheet sheet = ExamSheet.findExamSheetByScanAndStudentId(ex.scanfile.id,student.id).firstResult();

                String uuid = sheet.name;
                int studentnumber = (sheet.pagemin / (sheet.pagemax -  sheet.pagemin +1)) +1 ;
                var res = new StudentResultDTO();
                res.setNom(student.name);
                res.setPrenom(student.firstname);
                res.setIne(student.ine);
                res.setMail(student.mail);
                final DecimalFormat df = new DecimalFormat("0.00");
                res.setNote(df.format(r.note.doubleValue() / 100.0));
                res.setUuid(uuid);
                res.setStudentNumber(""+studentnumber);
                res.setAbi(false);
                res.setNotequestions(new HashMap<>());
                List<StudentResponse> resp =StudentResponse.findStudentResponsesbysheetId(sheet.id).list();
                resp.forEach(resp1->{

                    res.getNotequestions().put(resp1.question.numero,
                    df.format(
                    (resp1.note.doubleValue() *100.0 / resp1.question.step)/100.0));

                });
                results.add(res);

            }else {
                var res = new StudentResultDTO();
                res.setNom(student.name);
                res.setPrenom(student.firstname);
                res.setIne(student.ine);
                res.setMail(student.mail);
                res.setAbi(true);
                results.add(res);
            }
        });
        return Response.ok().entity(results).build();
    }

    @POST
    @Path("createstudentmasse")
    @Transactional
    @RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    public Response createAllStudent(StudentMassDTO dto, @Context SecurityContext ctx) {
        var userLogin = Optional
                .ofNullable(ctx.getUserPrincipal().getName());
        if (!userLogin.isPresent()) {
            throw new AccountResourceException("Current user login not found");
        }
        var user = User.findOneByLogin(userLogin.get());
        if (!user.isPresent()) {
            throw new AccountResourceException("User could not be found");
        }
        Course c = Course.findById(dto.getCourse());
        List<String> groupes = dto.getStudents().stream().map(e -> e.getGroupe()).distinct()
                .collect(Collectors.toList());
        Map<String, CourseGroup> groupesentities = new HashMap<>();
        groupes.forEach(g -> {
            long count = CourseGroup.findByNameandCourse(c.id,g).count();
            if (count >0){
                CourseGroup cgdest = CourseGroup.findByNameandCourse(c.id,g).firstResult();
                groupesentities.put(g, cgdest);
            } else{
                CourseGroup g1 = new CourseGroup();
                g1.course = c;
                g1.groupName = g;
                groupesentities.put(g, g1);

            }

        });
        dto.getStudents().forEach(s -> {
            Student st = new Student();
            st.ine = s.getIne();
            st.name = s.getNom();
            st.firstname = s.getPrenom();
            st.mail = s.getMail();
            Student st1 = Student.persistOrUpdate(st);
            groupesentities.get(s.getGroupe()).students.add(st1);
        });

        groupesentities.values().forEach(gc -> CourseGroup.persistOrUpdate(gc));
        return Response.ok().build();

    }



    @PUT
    @Path("updatestudent/{courseid}")
    @Transactional
    public Response updatestudent4Course(fr.istic.service.customdto.StudentDTO student ,@PathParam("courseid") long courseid,  @Context SecurityContext ctx) {
        var st = Student.findStudentsbyCourseIdAndINE(courseid, student.getIne()).firstResult();
        st.firstname = student.getPrenom();
        st.name = student.getNom();
        st.mail = student.getMail();
        Student.persistOrUpdate(st);
        return Response.ok().entity(st).build();
    }

    @PUT
    @Path("updatestudentgroup/{courseid}")
    @Transactional
    public Response updatestudentgroup(fr.istic.service.customdto.StudentDTO student ,@PathParam("courseid") long courseid,  @Context SecurityContext ctx) {
        var st = Student.findStudentsbyCourseIdAndINE(courseid, student.getIne()).firstResult();
        CourseGroup cgorigin = CourseGroup.findByStudentINEandCourse(courseid, student.getIne()).firstResult();
        cgorigin.students.remove(st);
        CourseGroup.persistOrUpdate(cgorigin);
        long count = CourseGroup.findByNameandCourse(courseid, student.getGroupe()).count();
        if (count >0){
            CourseGroup cgdest = CourseGroup.findByNameandCourse(courseid, student.getGroupe()).firstResult();
            cgdest.students.add(st);
            CourseGroup.persistOrUpdate(cgdest);
            return Response.ok().build();

        } else {
            CourseGroup cgdest = new CourseGroup();
            cgdest.groupName = student.getGroupe();
            cgdest.course = Course.findById(courseid);
            cgdest.students.add(st);
            CourseGroup.persistOrUpdate(cgdest);
            return Response.ok().build();
        }
    }
    @PUT
    @Path("updatestudentine/{courseid}")
    @Transactional
    public Response updatestudentine(fr.istic.service.customdto.StudentDTO student ,@PathParam("courseid") long courseid,  @Context SecurityContext ctx) {
        var st = Student.findStudentsbyCourseIdAndFirsNameAndLastName(courseid, student.getPrenom(), student.getNom()).firstResult();
        st.ine = student.getIne();
        Student.persistOrUpdate(st);
        return Response.ok().entity(st).build();
    }


    @GET
    @Path("getstudentcours/{courseid}")
    @Transactional
    public Response getAllStudent4Course(@PathParam("courseid") long courseid, @Context SecurityContext ctx) {
        var userLogin = Optional
                .ofNullable(ctx.getUserPrincipal().getName());
        if (!userLogin.isPresent()) {
            throw new AccountResourceException("Current user login not found");
        }
        var user = User.findOneByLogin(userLogin.get());
        if (!user.isPresent()) {
            throw new AccountResourceException("User could not be found");
        }

        Course c = Course.findById(courseid);
        List<fr.istic.service.customdto.StudentDTO> students = new ArrayList<>();
        if (c != null) {
            c.groups.forEach(g -> {
                CourseGroup.findOneWithEagerRelationships(g.id).get().students.forEach(s -> {
                    fr.istic.service.customdto.StudentDTO sdto = new fr.istic.service.customdto.StudentDTO();
                    sdto.setIne(s.ine);
                    sdto.setNom(s.name);
                    sdto.setPrenom(s.firstname);
                    sdto.setMail(s.mail);
                    sdto.setGroupe(g.groupName);
                    students.add(sdto);
                });
            });

        }
        return Response.ok().entity(students).build();
    }

    @DELETE
    @Path("deletegroupstudents/{courseid}")
    @RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    @Transactional
    public Response deleteAllStudent4Course(@PathParam("courseid") long courseid, @Context SecurityContext ctx) {
        var userLogin = Optional
                .ofNullable(ctx.getUserPrincipal().getName());
        if (!userLogin.isPresent()) {
            throw new AccountResourceException("Current user login not found");
        }
        var user = User.findOneByLogin(userLogin.get());
        if (!user.isPresent()) {
            throw new AccountResourceException("User could not be found");
        }
        Course c = Course.findById(courseid);
        c.groups.forEach(g -> {
            g.students.forEach(st -> {
                st.groups.remove(g);
                Student.update(st);
            });
            CourseGroup.deleteById(g.id);
        });
        c.groups.clear();
        Course.update(c);
        return Response.ok().build();
    }


	@GET
	@Path("summary/exam/{examId}")
	@RolesAllowed({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
	@Transactional
	public Response getSummaryExam(@PathParam("examId") final long examId) {
		final Exam exam = Exam.findById(examId);

		System.out.println(exam);
		System.out.println(exam.scanfile);

		if(exam == null || exam.scanfile == null || exam.scanfile.sheets == null) {
			return Response.status(Response.Status.BAD_REQUEST).build();
		}

		// Getting all the questions of the exam, sorted by ID
		final List<Question> questions = Question.findQuestionbyExamId(examId)
			.list()
			.stream().sorted(Comparator.comparingLong(std -> std.id))
			.collect(Collectors.toList());

		final Map<Long, List<StudentResponse>> responsesBySheet = exam.scanfile.sheets
			.parallelStream()
			// Getting all the responses of each sheet, and sorting the responses by their question id
			.map(sh -> Pair.of(sh.id, StudentResponse.findStudentResponsesbysheetId(sh.id)
					.list().stream().sorted(Comparator.comparingLong(std -> std.question.id)).collect(Collectors.toList())))
			.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

		// Producing DTOs for sheets
		final var sheetsDTO = responsesBySheet
			.entrySet()
			.stream()
			.sorted(Comparator.comparingLong(Map.Entry::getKey))
			.map(sh -> {
				final List<StudentResponse> resp = sh.getValue();
				return createSummaryElementDTO(resp, sh.getKey(), questions.size(), i -> resp.get(i).question.id);
			})
			.collect(Collectors.toList());

		// Sorting responses by questions
		final var responsesByQuestion = responsesBySheet.values()
			.stream()
			.flatMap(s -> s.stream())
			.collect(Collectors.groupingBy(e -> e.question.id));

		// Producing DTOs for questions
		final var questionsDTO = questions
			.stream()
			.map(q -> {
				final List<StudentResponse> resp = responsesByQuestion.get(q.id);
				return createSummaryElementDTO(resp, q.id, exam.scanfile.sheets.size(), i -> resp.get(i).sheet.id);
			})
			.collect(Collectors.toList());

			return Response.ok().entity(new SummaryExamDTO(questionsDTO, sheetsDTO)).build();
	}


	private SummaryElementDTO createSummaryElementDTO(final List<StudentResponse> resp, final Long id, final int size,
		final Function<Integer, Long> getterID) {
		if(resp == null) {
			return new SummaryElementDTO(id, 1, 0);
		}else {
			int firstUnrated = 0;

			if(resp.size() != size) {
				for(int i = 0; i < resp.size() && firstUnrated != getterID.apply(i); i++) {
					firstUnrated++;
				}
			}

			return new SummaryElementDTO(id, firstUnrated, resp.size());
		}
	}
}

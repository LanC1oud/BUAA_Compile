; ModuleID = 'runtime/libsysy.c'
source_filename = "runtime/libsysy.c"
target datalayout = "e-m:w-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-windows-msvc"

%struct.__crt_locale_pointers = type { %struct.__crt_locale_data*, %struct.__crt_multibyte_data* }
%struct.__crt_locale_data = type opaque
%struct.__crt_multibyte_data = type opaque
%struct._iobuf = type { i8* }

$sprintf = comdat any

$vsprintf = comdat any

$_snprintf = comdat any

$_vsnprintf = comdat any

$fprintf = comdat any

$printf = comdat any

$_vsprintf_l = comdat any

$_vsnprintf_l = comdat any

$__local_stdio_printf_options = comdat any

$_vfprintf_l = comdat any

$"??_C@_1CE@FEAELLHK@?$AAr?$AAu?$AAn?$AAt?$AAi?$AAm?$AAe?$AA?1?$AAl?$AAi?$AAb?$AAs?$AAy?$AAs?$AAy?$AA?4?$AAc?$AA?$AA@" = comdat any

$"??_C@_1BG@FFFFMINI@?$AAv?$AAa?$AAl?$AAu?$AAe?$AA?5?$AA?$DO?$AA?$DN?$AA?5?$AA0?$AA?$AA@" = comdat any

$"??_C@_0BO@MHIJBILO@libsys?3?5get_int?3?5EOF?5reached?6?$AA@" = comdat any

$"??_C@_0BN@FBJFLIIM@libsys?3?5get_int?3?5empty?5line?6?$AA@" = comdat any

$"??_C@_0CB@PNFIGALK@libsys?3?5get_int?3?5invalid?5digits?6@" = comdat any

$"??_C@_0CO@LIBPJGIK@libsys?3?5get_int?3?5invalid?5trailin@" = comdat any

$"??_C@_0CH@FOPHCIE@libsys?3?5get_int?3?5integer?5out?5of?5@" = comdat any

$"??_C@_0CF@HBBGFCAL@libsys?3?5get_string?3?5input?5error?3@" = comdat any

$"??_C@_0DH@IDKMLKKI@libsys?3?5get_string?3?5failed?5to?5re@" = comdat any

$"??_C@_0CB@KOGANMPM@libsys?3?5get_string?3?5EOF?5reached?6@" = comdat any

$"??_C@_02DPKJAMEF@?$CFd?$AA@" = comdat any

$"??_C@_02HAOIJKIC@?$CFc?$AA@" = comdat any

$"??_C@_02DKCKIIND@?$CFs?$AA@" = comdat any

@"??_C@_1CE@FEAELLHK@?$AAr?$AAu?$AAn?$AAt?$AAi?$AAm?$AAe?$AA?1?$AAl?$AAi?$AAb?$AAs?$AAy?$AAs?$AAy?$AA?4?$AAc?$AA?$AA@" = linkonce_odr dso_local unnamed_addr constant [18 x i16] [i16 114, i16 117, i16 110, i16 116, i16 105, i16 109, i16 101, i16 47, i16 108, i16 105, i16 98, i16 115, i16 121, i16 115, i16 121, i16 46, i16 99, i16 0], comdat, align 2
@"??_C@_1BG@FFFFMINI@?$AAv?$AAa?$AAl?$AAu?$AAe?$AA?5?$AA?$DO?$AA?$DN?$AA?5?$AA0?$AA?$AA@" = linkonce_odr dso_local unnamed_addr constant [11 x i16] [i16 118, i16 97, i16 108, i16 117, i16 101, i16 32, i16 62, i16 61, i16 32, i16 48, i16 0], comdat, align 2
@"??_C@_0BO@MHIJBILO@libsys?3?5get_int?3?5EOF?5reached?6?$AA@" = linkonce_odr dso_local unnamed_addr constant [30 x i8] c"libsys: get_int: EOF reached\0A\00", comdat, align 1
@"??_C@_0BN@FBJFLIIM@libsys?3?5get_int?3?5empty?5line?6?$AA@" = linkonce_odr dso_local unnamed_addr constant [29 x i8] c"libsys: get_int: empty line\0A\00", comdat, align 1
@"??_C@_0CB@PNFIGALK@libsys?3?5get_int?3?5invalid?5digits?6@" = linkonce_odr dso_local unnamed_addr constant [33 x i8] c"libsys: get_int: invalid digits\0A\00", comdat, align 1
@"??_C@_0CO@LIBPJGIK@libsys?3?5get_int?3?5invalid?5trailin@" = linkonce_odr dso_local unnamed_addr constant [46 x i8] c"libsys: get_int: invalid trailing characters\0A\00", comdat, align 1
@"??_C@_0CH@FOPHCIE@libsys?3?5get_int?3?5integer?5out?5of?5@" = linkonce_odr dso_local unnamed_addr constant [39 x i8] c"libsys: get_int: integer out of range\0A\00", comdat, align 1
@"??_C@_0CF@HBBGFCAL@libsys?3?5get_string?3?5input?5error?3@" = linkonce_odr dso_local unnamed_addr constant [37 x i8] c"libsys: get_string: input error: %s\0A\00", comdat, align 1
@"??_C@_0DH@IDKMLKKI@libsys?3?5get_string?3?5failed?5to?5re@" = linkonce_odr dso_local unnamed_addr constant [55 x i8] c"libsys: get_string: failed to restore input character\0A\00", comdat, align 1
@"??_C@_0CB@KOGANMPM@libsys?3?5get_string?3?5EOF?5reached?6@" = linkonce_odr dso_local unnamed_addr constant [33 x i8] c"libsys: get_string: EOF reached\0A\00", comdat, align 1
@"??_C@_02DPKJAMEF@?$CFd?$AA@" = linkonce_odr dso_local unnamed_addr constant [3 x i8] c"%d\00", comdat, align 1
@"??_C@_02HAOIJKIC@?$CFc?$AA@" = linkonce_odr dso_local unnamed_addr constant [3 x i8] c"%c\00", comdat, align 1
@"??_C@_02DKCKIIND@?$CFs?$AA@" = linkonce_odr dso_local unnamed_addr constant [3 x i8] c"%s\00", comdat, align 1
@__local_stdio_printf_options._OptionsStorage = internal global i64 0, align 8

; Function Attrs: nobuiltin noinline nounwind optnone
define linkonce_odr dso_local i32 @sprintf(i8* %_Buffer, i8* %_Format, ...) #0 comdat {
entry:
  %_Format.addr = alloca i8*, align 8
  %_Buffer.addr = alloca i8*, align 8
  %_Result = alloca i32, align 4
  %_ArgList = alloca i8*, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %_ArgList1 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_start(i8* %_ArgList1)
  %0 = load i8*, i8** %_ArgList, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %2 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i32 @_vsprintf_l(i8* %2, i8* %1, %struct.__crt_locale_pointers* null, i8* %0)
  store i32 %call, i32* %_Result, align 4
  %_ArgList2 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_end(i8* %_ArgList2)
  %3 = load i32, i32* %_Result, align 4
  ret i32 %3
}

; Function Attrs: nobuiltin noinline nounwind optnone
define linkonce_odr dso_local i32 @vsprintf(i8* %_Buffer, i8* %_Format, i8* %_ArgList) #0 comdat {
entry:
  %_ArgList.addr = alloca i8*, align 8
  %_Format.addr = alloca i8*, align 8
  %_Buffer.addr = alloca i8*, align 8
  store i8* %_ArgList, i8** %_ArgList.addr, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %0 = load i8*, i8** %_ArgList.addr, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %2 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i32 @_vsnprintf_l(i8* %2, i64 -1, i8* %1, %struct.__crt_locale_pointers* null, i8* %0)
  ret i32 %call
}

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i32 @_snprintf(i8* %_Buffer, i64 %_BufferCount, i8* %_Format, ...) #1 comdat {
entry:
  %_Format.addr = alloca i8*, align 8
  %_BufferCount.addr = alloca i64, align 8
  %_Buffer.addr = alloca i8*, align 8
  %_Result = alloca i32, align 4
  %_ArgList = alloca i8*, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i64 %_BufferCount, i64* %_BufferCount.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %_ArgList1 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_start(i8* %_ArgList1)
  %0 = load i8*, i8** %_ArgList, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %2 = load i64, i64* %_BufferCount.addr, align 8
  %3 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i32 @_vsnprintf(i8* %3, i64 %2, i8* %1, i8* %0)
  store i32 %call, i32* %_Result, align 4
  %_ArgList2 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_end(i8* %_ArgList2)
  %4 = load i32, i32* %_Result, align 4
  ret i32 %4
}

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i32 @_vsnprintf(i8* %_Buffer, i64 %_BufferCount, i8* %_Format, i8* %_ArgList) #1 comdat {
entry:
  %_ArgList.addr = alloca i8*, align 8
  %_Format.addr = alloca i8*, align 8
  %_BufferCount.addr = alloca i64, align 8
  %_Buffer.addr = alloca i8*, align 8
  store i8* %_ArgList, i8** %_ArgList.addr, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i64 %_BufferCount, i64* %_BufferCount.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %0 = load i8*, i8** %_ArgList.addr, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %2 = load i64, i64* %_BufferCount.addr, align 8
  %3 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i32 @_vsnprintf_l(i8* %3, i64 %2, i8* %1, %struct.__crt_locale_pointers* null, i8* %0)
  ret i32 %call
}

; Function Attrs: noinline nounwind optnone
define dso_local i8 @get_char() #1 {
entry:
  %value = alloca i32, align 4
  %call = call i32 @getchar()
  store i32 %call, i32* %value, align 4
  %0 = load i32, i32* %value, align 4
  %cmp = icmp sge i32 %0, 0
  br i1 %cmp, label %lor.end, label %lor.rhs

lor.rhs:                                          ; preds = %entry
  call void @_wassert(i16* getelementptr inbounds ([11 x i16], [11 x i16]* @"??_C@_1BG@FFFFMINI@?$AAv?$AAa?$AAl?$AAu?$AAe?$AA?5?$AA?$DO?$AA?$DN?$AA?5?$AA0?$AA?$AA@", i64 0, i64 0), i16* getelementptr inbounds ([18 x i16], [18 x i16]* @"??_C@_1CE@FEAELLHK@?$AAr?$AAu?$AAn?$AAt?$AAi?$AAm?$AAe?$AA?1?$AAl?$AAi?$AAb?$AAs?$AAy?$AAs?$AAy?$AA?4?$AAc?$AA?$AA@", i64 0, i64 0), i32 16)
  br label %lor.end

lor.end:                                          ; preds = %lor.rhs, %entry
  %1 = phi i1 [ true, %entry ], [ false, %lor.rhs ]
  %lor.ext = zext i1 %1 to i32
  %2 = load i32, i32* %value, align 4
  %conv = trunc i32 %2 to i8
  ret i8 %conv
}

declare dso_local i32 @getchar() #2

declare dso_local void @_wassert(i16*, i16*, i32) #2

; Function Attrs: noinline nounwind optnone
define dso_local i32 @get_int() #1 {
entry:
  %input_buffer = alloca [512 x i8], align 16
  %p = alloca i8*, align 8
  %end_ptr = alloca i8*, align 8
  %value = alloca i32, align 4
  %call = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %arraydecay = getelementptr inbounds [512 x i8], [512 x i8]* %input_buffer, i64 0, i64 0
  %call1 = call i8* @fgets(i8* %arraydecay, i32 512, %struct._iobuf* %call)
  %cmp = icmp eq i8* %call1, null
  br i1 %cmp, label %if.then, label %if.end

if.then:                                          ; preds = %entry
  %call2 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call3 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call2, i8* getelementptr inbounds ([30 x i8], [30 x i8]* @"??_C@_0BO@MHIJBILO@libsys?3?5get_int?3?5EOF?5reached?6?$AA@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end:                                           ; preds = %entry
  %arraydecay4 = getelementptr inbounds [512 x i8], [512 x i8]* %input_buffer, i64 0, i64 0
  store i8* %arraydecay4, i8** %p, align 8
  br label %while.cond

while.cond:                                       ; preds = %while.body, %if.end
  %0 = load i8*, i8** %p, align 8
  %1 = load i8, i8* %0, align 1
  %conv = zext i8 %1 to i32
  %call5 = call i32 @isspace(i32 %conv) #7
  %tobool = icmp ne i32 %call5, 0
  br i1 %tobool, label %while.body, label %while.end

while.body:                                       ; preds = %while.cond
  %2 = load i8*, i8** %p, align 8
  %incdec.ptr = getelementptr inbounds i8, i8* %2, i32 1
  store i8* %incdec.ptr, i8** %p, align 8
  br label %while.cond, !llvm.loop !2

while.end:                                        ; preds = %while.cond
  %3 = load i8*, i8** %p, align 8
  %4 = load i8, i8* %3, align 1
  %conv6 = zext i8 %4 to i32
  %cmp7 = icmp eq i32 %conv6, 0
  br i1 %cmp7, label %if.then9, label %if.end12

if.then9:                                         ; preds = %while.end
  %call10 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call11 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call10, i8* getelementptr inbounds ([29 x i8], [29 x i8]* @"??_C@_0BN@FBJFLIIM@libsys?3?5get_int?3?5empty?5line?6?$AA@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end12:                                         ; preds = %while.end
  %call13 = call i32* @_errno()
  store i32 0, i32* %call13, align 4
  store i8* null, i8** %end_ptr, align 8
  %5 = load i8*, i8** %p, align 8
  %call14 = call i32 @strtol(i8* %5, i8** %end_ptr, i32 10)
  store i32 %call14, i32* %value, align 4
  %6 = load i8*, i8** %p, align 8
  %7 = load i8*, i8** %end_ptr, align 8
  %cmp15 = icmp eq i8* %6, %7
  br i1 %cmp15, label %if.then17, label %if.end20

if.then17:                                        ; preds = %if.end12
  %call18 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call19 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call18, i8* getelementptr inbounds ([33 x i8], [33 x i8]* @"??_C@_0CB@PNFIGALK@libsys?3?5get_int?3?5invalid?5digits?6@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end20:                                         ; preds = %if.end12
  br label %while.cond21

while.cond21:                                     ; preds = %while.body25, %if.end20
  %8 = load i8*, i8** %end_ptr, align 8
  %9 = load i8, i8* %8, align 1
  %conv22 = zext i8 %9 to i32
  %call23 = call i32 @isspace(i32 %conv22) #7
  %tobool24 = icmp ne i32 %call23, 0
  br i1 %tobool24, label %while.body25, label %while.end27

while.body25:                                     ; preds = %while.cond21
  %10 = load i8*, i8** %end_ptr, align 8
  %incdec.ptr26 = getelementptr inbounds i8, i8* %10, i32 1
  store i8* %incdec.ptr26, i8** %end_ptr, align 8
  br label %while.cond21, !llvm.loop !4

while.end27:                                      ; preds = %while.cond21
  %11 = load i8*, i8** %end_ptr, align 8
  %12 = load i8, i8* %11, align 1
  %conv28 = zext i8 %12 to i32
  %cmp29 = icmp ne i32 %conv28, 0
  br i1 %cmp29, label %if.then31, label %if.end34

if.then31:                                        ; preds = %while.end27
  %call32 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call33 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call32, i8* getelementptr inbounds ([46 x i8], [46 x i8]* @"??_C@_0CO@LIBPJGIK@libsys?3?5get_int?3?5invalid?5trailin@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end34:                                         ; preds = %while.end27
  %call35 = call i32* @_errno()
  %13 = load i32, i32* %call35, align 4
  %cmp36 = icmp eq i32 %13, 34
  br i1 %cmp36, label %if.then43, label %lor.lhs.false

lor.lhs.false:                                    ; preds = %if.end34
  %14 = load i32, i32* %value, align 4
  %cmp38 = icmp slt i32 %14, -2147483648
  br i1 %cmp38, label %if.then43, label %lor.lhs.false40

lor.lhs.false40:                                  ; preds = %lor.lhs.false
  %15 = load i32, i32* %value, align 4
  %cmp41 = icmp sgt i32 %15, 2147483647
  br i1 %cmp41, label %if.then43, label %if.end46

if.then43:                                        ; preds = %lor.lhs.false40, %lor.lhs.false, %if.end34
  %call44 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call45 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call44, i8* getelementptr inbounds ([39 x i8], [39 x i8]* @"??_C@_0CH@FOPHCIE@libsys?3?5get_int?3?5integer?5out?5of?5@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end46:                                         ; preds = %lor.lhs.false40
  %16 = load i32, i32* %value, align 4
  ret i32 %16
}

declare dso_local i8* @fgets(i8*, i32, %struct._iobuf*) #2

declare dso_local %struct._iobuf* @__acrt_iob_func(i32) #2

; Function Attrs: nobuiltin noinline nounwind optnone
define linkonce_odr dso_local i32 @fprintf(%struct._iobuf* %_Stream, i8* %_Format, ...) #0 comdat {
entry:
  %_Format.addr = alloca i8*, align 8
  %_Stream.addr = alloca %struct._iobuf*, align 8
  %_Result = alloca i32, align 4
  %_ArgList = alloca i8*, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store %struct._iobuf* %_Stream, %struct._iobuf** %_Stream.addr, align 8
  %_ArgList1 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_start(i8* %_ArgList1)
  %0 = load i8*, i8** %_ArgList, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %2 = load %struct._iobuf*, %struct._iobuf** %_Stream.addr, align 8
  %call = call i32 @_vfprintf_l(%struct._iobuf* %2, i8* %1, %struct.__crt_locale_pointers* null, i8* %0)
  store i32 %call, i32* %_Result, align 4
  %_ArgList2 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_end(i8* %_ArgList2)
  %3 = load i32, i32* %_Result, align 4
  ret i32 %3
}

; Function Attrs: noreturn
declare dso_local void @abort() #3

; Function Attrs: nounwind readonly willreturn
declare dso_local i32 @isspace(i32) #4

declare dso_local i32* @_errno() #2

declare dso_local i32 @strtol(i8*, i8**, i32) #2

; Function Attrs: noinline nounwind optnone
define dso_local void @get_string(i8* %buffer, i32 %max_len) #1 {
entry:
  %max_len.addr = alloca i32, align 4
  %buffer.addr = alloca i8*, align 8
  %stored_length = alloca i32, align 4
  %max_stored_length = alloca i32, align 4
  %read_anything = alloca i32, align 4
  %value = alloca i32, align 4
  %next_value = alloca i32, align 4
  store i32 %max_len, i32* %max_len.addr, align 4
  store i8* %buffer, i8** %buffer.addr, align 8
  store i32 0, i32* %stored_length, align 4
  %0 = load i32, i32* %max_len.addr, align 4
  %cmp = icmp sgt i32 %0, 0
  br i1 %cmp, label %cond.true, label %cond.false

cond.true:                                        ; preds = %entry
  %1 = load i32, i32* %max_len.addr, align 4
  %sub = sub nsw i32 %1, 1
  br label %cond.end

cond.false:                                       ; preds = %entry
  br label %cond.end

cond.end:                                         ; preds = %cond.false, %cond.true
  %cond = phi i32 [ %sub, %cond.true ], [ 0, %cond.false ]
  store i32 %cond, i32* %max_stored_length, align 4
  store i32 0, i32* %read_anything, align 4
  br label %while.cond

while.cond:                                       ; preds = %if.end30, %cond.end
  %call = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %call1 = call i32 @fgetc(%struct._iobuf* %call)
  store i32 %call1, i32* %value, align 4
  %cmp2 = icmp ne i32 %call1, -1
  br i1 %cmp2, label %while.body, label %while.end

while.body:                                       ; preds = %while.cond
  store i32 1, i32* %read_anything, align 4
  %2 = load i32, i32* %value, align 4
  %cmp3 = icmp eq i32 %2, 10
  br i1 %cmp3, label %if.then, label %if.end

if.then:                                          ; preds = %while.body
  br label %while.end

if.end:                                           ; preds = %while.body
  %3 = load i32, i32* %value, align 4
  %cmp4 = icmp eq i32 %3, 13
  br i1 %cmp4, label %if.then5, label %if.end27

if.then5:                                         ; preds = %if.end
  %call6 = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %call7 = call i32 @fgetc(%struct._iobuf* %call6)
  store i32 %call7, i32* %next_value, align 4
  %4 = load i32, i32* %next_value, align 4
  %cmp8 = icmp eq i32 %4, -1
  br i1 %cmp8, label %if.then9, label %if.else

if.then9:                                         ; preds = %if.then5
  %call10 = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %call11 = call i32 @ferror(%struct._iobuf* %call10)
  %tobool = icmp ne i32 %call11, 0
  br i1 %tobool, label %if.then12, label %if.end17

if.then12:                                        ; preds = %if.then9
  %call13 = call i32* @_errno()
  %5 = load i32, i32* %call13, align 4
  %call14 = call i8* @strerror(i32 %5)
  %call15 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call16 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call15, i8* getelementptr inbounds ([37 x i8], [37 x i8]* @"??_C@_0CF@HBBGFCAL@libsys?3?5get_string?3?5input?5error?3@", i64 0, i64 0), i8* %call14)
  call void @abort() #6
  unreachable

if.end17:                                         ; preds = %if.then9
  br label %if.end26

if.else:                                          ; preds = %if.then5
  %6 = load i32, i32* %next_value, align 4
  %cmp18 = icmp ne i32 %6, 10
  br i1 %cmp18, label %land.lhs.true, label %if.end25

land.lhs.true:                                    ; preds = %if.else
  %call19 = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %7 = load i32, i32* %next_value, align 4
  %call20 = call i32 @ungetc(i32 %7, %struct._iobuf* %call19)
  %cmp21 = icmp eq i32 %call20, -1
  br i1 %cmp21, label %if.then22, label %if.end25

if.then22:                                        ; preds = %land.lhs.true
  %call23 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call24 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call23, i8* getelementptr inbounds ([55 x i8], [55 x i8]* @"??_C@_0DH@IDKMLKKI@libsys?3?5get_string?3?5failed?5to?5re@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end25:                                         ; preds = %land.lhs.true, %if.else
  br label %if.end26

if.end26:                                         ; preds = %if.end25, %if.end17
  br label %while.end

if.end27:                                         ; preds = %if.end
  %8 = load i32, i32* %stored_length, align 4
  %9 = load i32, i32* %max_stored_length, align 4
  %cmp28 = icmp slt i32 %8, %9
  br i1 %cmp28, label %if.then29, label %if.end30

if.then29:                                        ; preds = %if.end27
  %10 = load i32, i32* %value, align 4
  %conv = trunc i32 %10 to i8
  %11 = load i8*, i8** %buffer.addr, align 8
  %12 = load i32, i32* %stored_length, align 4
  %idxprom = sext i32 %12 to i64
  %arrayidx = getelementptr inbounds i8, i8* %11, i64 %idxprom
  store i8 %conv, i8* %arrayidx, align 1
  %13 = load i32, i32* %stored_length, align 4
  %inc = add nsw i32 %13, 1
  store i32 %inc, i32* %stored_length, align 4
  br label %if.end30

if.end30:                                         ; preds = %if.then29, %if.end27
  br label %while.cond, !llvm.loop !5

while.end:                                        ; preds = %if.end26, %if.then, %while.cond
  %14 = load i32, i32* %value, align 4
  %cmp31 = icmp eq i32 %14, -1
  br i1 %cmp31, label %if.then33, label %if.end48

if.then33:                                        ; preds = %while.end
  %call34 = call %struct._iobuf* @__acrt_iob_func(i32 0)
  %call35 = call i32 @ferror(%struct._iobuf* %call34)
  %tobool36 = icmp ne i32 %call35, 0
  br i1 %tobool36, label %if.then37, label %if.end42

if.then37:                                        ; preds = %if.then33
  %call38 = call i32* @_errno()
  %15 = load i32, i32* %call38, align 4
  %call39 = call i8* @strerror(i32 %15)
  %call40 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call41 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call40, i8* getelementptr inbounds ([37 x i8], [37 x i8]* @"??_C@_0CF@HBBGFCAL@libsys?3?5get_string?3?5input?5error?3@", i64 0, i64 0), i8* %call39)
  call void @abort() #6
  unreachable

if.end42:                                         ; preds = %if.then33
  %16 = load i32, i32* %read_anything, align 4
  %tobool43 = icmp ne i32 %16, 0
  br i1 %tobool43, label %if.end47, label %if.then44

if.then44:                                        ; preds = %if.end42
  %call45 = call %struct._iobuf* @__acrt_iob_func(i32 2)
  %call46 = call i32 (%struct._iobuf*, i8*, ...) @fprintf(%struct._iobuf* %call45, i8* getelementptr inbounds ([33 x i8], [33 x i8]* @"??_C@_0CB@KOGANMPM@libsys?3?5get_string?3?5EOF?5reached?6@", i64 0, i64 0))
  call void @abort() #6
  unreachable

if.end47:                                         ; preds = %if.end42
  br label %if.end48

if.end48:                                         ; preds = %if.end47, %while.end
  %17 = load i32, i32* %max_len.addr, align 4
  %cmp49 = icmp sle i32 %17, 0
  br i1 %cmp49, label %if.then51, label %if.end52

if.then51:                                        ; preds = %if.end48
  br label %return

if.end52:                                         ; preds = %if.end48
  %18 = load i32, i32* %stored_length, align 4
  %19 = load i32, i32* %max_stored_length, align 4
  %cmp53 = icmp slt i32 %18, %19
  br i1 %cmp53, label %if.then55, label %if.end59

if.then55:                                        ; preds = %if.end52
  %20 = load i8*, i8** %buffer.addr, align 8
  %21 = load i32, i32* %stored_length, align 4
  %idxprom56 = sext i32 %21 to i64
  %arrayidx57 = getelementptr inbounds i8, i8* %20, i64 %idxprom56
  store i8 10, i8* %arrayidx57, align 1
  %22 = load i32, i32* %stored_length, align 4
  %inc58 = add nsw i32 %22, 1
  store i32 %inc58, i32* %stored_length, align 4
  br label %if.end59

if.end59:                                         ; preds = %if.then55, %if.end52
  %23 = load i8*, i8** %buffer.addr, align 8
  %24 = load i32, i32* %stored_length, align 4
  %idxprom60 = sext i32 %24 to i64
  %arrayidx61 = getelementptr inbounds i8, i8* %23, i64 %idxprom60
  store i8 0, i8* %arrayidx61, align 1
  br label %return

return:                                           ; preds = %if.end59, %if.then51
  ret void
}

declare dso_local i32 @fgetc(%struct._iobuf*) #2

declare dso_local i32 @ferror(%struct._iobuf*) #2

declare dso_local i8* @strerror(i32) #2

declare dso_local i32 @ungetc(i32, %struct._iobuf*) #2

; Function Attrs: noinline nounwind optnone
define dso_local void @put_int(i32 %a) #1 {
entry:
  %a.addr = alloca i32, align 4
  store i32 %a, i32* %a.addr, align 4
  %0 = load i32, i32* %a.addr, align 4
  %call = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([3 x i8], [3 x i8]* @"??_C@_02DPKJAMEF@?$CFd?$AA@", i64 0, i64 0), i32 %0)
  ret void
}

; Function Attrs: nobuiltin noinline nounwind optnone
define linkonce_odr dso_local i32 @printf(i8* %_Format, ...) #0 comdat {
entry:
  %_Format.addr = alloca i8*, align 8
  %_Result = alloca i32, align 4
  %_ArgList = alloca i8*, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  %_ArgList1 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_start(i8* %_ArgList1)
  %0 = load i8*, i8** %_ArgList, align 8
  %1 = load i8*, i8** %_Format.addr, align 8
  %call = call %struct._iobuf* @__acrt_iob_func(i32 1)
  %call2 = call i32 @_vfprintf_l(%struct._iobuf* %call, i8* %1, %struct.__crt_locale_pointers* null, i8* %0)
  store i32 %call2, i32* %_Result, align 4
  %_ArgList3 = bitcast i8** %_ArgList to i8*
  call void @llvm.va_end(i8* %_ArgList3)
  %2 = load i32, i32* %_Result, align 4
  ret i32 %2
}

; Function Attrs: noinline nounwind optnone
define dso_local void @put_char(i8 %a) #1 {
entry:
  %a.addr = alloca i8, align 1
  store i8 %a, i8* %a.addr, align 1
  %0 = load i8, i8* %a.addr, align 1
  %conv = zext i8 %0 to i32
  %call = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([3 x i8], [3 x i8]* @"??_C@_02HAOIJKIC@?$CFc?$AA@", i64 0, i64 0), i32 %conv)
  ret void
}

; Function Attrs: noinline nounwind optnone
define dso_local void @put_string(i8* %str) #1 {
entry:
  %str.addr = alloca i8*, align 8
  store i8* %str, i8** %str.addr, align 8
  %0 = load i8*, i8** %str.addr, align 8
  %call = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([3 x i8], [3 x i8]* @"??_C@_02DKCKIIND@?$CFs?$AA@", i64 0, i64 0), i8* %0)
  ret void
}

; Function Attrs: nofree nosync nounwind willreturn
declare void @llvm.va_start(i8*) #5

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i32 @_vsprintf_l(i8* %_Buffer, i8* %_Format, %struct.__crt_locale_pointers* %_Locale, i8* %_ArgList) #1 comdat {
entry:
  %_ArgList.addr = alloca i8*, align 8
  %_Locale.addr = alloca %struct.__crt_locale_pointers*, align 8
  %_Format.addr = alloca i8*, align 8
  %_Buffer.addr = alloca i8*, align 8
  store i8* %_ArgList, i8** %_ArgList.addr, align 8
  store %struct.__crt_locale_pointers* %_Locale, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %0 = load i8*, i8** %_ArgList.addr, align 8
  %1 = load %struct.__crt_locale_pointers*, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  %2 = load i8*, i8** %_Format.addr, align 8
  %3 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i32 @_vsnprintf_l(i8* %3, i64 -1, i8* %2, %struct.__crt_locale_pointers* %1, i8* %0)
  ret i32 %call
}

; Function Attrs: nofree nosync nounwind willreturn
declare void @llvm.va_end(i8*) #5

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i32 @_vsnprintf_l(i8* %_Buffer, i64 %_BufferCount, i8* %_Format, %struct.__crt_locale_pointers* %_Locale, i8* %_ArgList) #1 comdat {
entry:
  %_ArgList.addr = alloca i8*, align 8
  %_Locale.addr = alloca %struct.__crt_locale_pointers*, align 8
  %_Format.addr = alloca i8*, align 8
  %_BufferCount.addr = alloca i64, align 8
  %_Buffer.addr = alloca i8*, align 8
  %_Result = alloca i32, align 4
  store i8* %_ArgList, i8** %_ArgList.addr, align 8
  store %struct.__crt_locale_pointers* %_Locale, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store i64 %_BufferCount, i64* %_BufferCount.addr, align 8
  store i8* %_Buffer, i8** %_Buffer.addr, align 8
  %0 = load i8*, i8** %_ArgList.addr, align 8
  %1 = load %struct.__crt_locale_pointers*, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  %2 = load i8*, i8** %_Format.addr, align 8
  %3 = load i64, i64* %_BufferCount.addr, align 8
  %4 = load i8*, i8** %_Buffer.addr, align 8
  %call = call i64* @__local_stdio_printf_options()
  %5 = load i64, i64* %call, align 8
  %or = or i64 %5, 1
  %call1 = call i32 @__stdio_common_vsprintf(i64 %or, i8* %4, i64 %3, i8* %2, %struct.__crt_locale_pointers* %1, i8* %0)
  store i32 %call1, i32* %_Result, align 4
  %6 = load i32, i32* %_Result, align 4
  %cmp = icmp slt i32 %6, 0
  br i1 %cmp, label %cond.true, label %cond.false

cond.true:                                        ; preds = %entry
  br label %cond.end

cond.false:                                       ; preds = %entry
  %7 = load i32, i32* %_Result, align 4
  br label %cond.end

cond.end:                                         ; preds = %cond.false, %cond.true
  %cond = phi i32 [ -1, %cond.true ], [ %7, %cond.false ]
  ret i32 %cond
}

declare dso_local i32 @__stdio_common_vsprintf(i64, i8*, i64, i8*, %struct.__crt_locale_pointers*, i8*) #2

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i64* @__local_stdio_printf_options() #1 comdat {
entry:
  ret i64* @__local_stdio_printf_options._OptionsStorage
}

; Function Attrs: noinline nounwind optnone
define linkonce_odr dso_local i32 @_vfprintf_l(%struct._iobuf* %_Stream, i8* %_Format, %struct.__crt_locale_pointers* %_Locale, i8* %_ArgList) #1 comdat {
entry:
  %_ArgList.addr = alloca i8*, align 8
  %_Locale.addr = alloca %struct.__crt_locale_pointers*, align 8
  %_Format.addr = alloca i8*, align 8
  %_Stream.addr = alloca %struct._iobuf*, align 8
  store i8* %_ArgList, i8** %_ArgList.addr, align 8
  store %struct.__crt_locale_pointers* %_Locale, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  store i8* %_Format, i8** %_Format.addr, align 8
  store %struct._iobuf* %_Stream, %struct._iobuf** %_Stream.addr, align 8
  %0 = load i8*, i8** %_ArgList.addr, align 8
  %1 = load %struct.__crt_locale_pointers*, %struct.__crt_locale_pointers** %_Locale.addr, align 8
  %2 = load i8*, i8** %_Format.addr, align 8
  %3 = load %struct._iobuf*, %struct._iobuf** %_Stream.addr, align 8
  %call = call i64* @__local_stdio_printf_options()
  %4 = load i64, i64* %call, align 8
  %call1 = call i32 @__stdio_common_vfprintf(i64 %4, %struct._iobuf* %3, i8* %2, %struct.__crt_locale_pointers* %1, i8* %0)
  ret i32 %call1
}

declare dso_local i32 @__stdio_common_vfprintf(i64, %struct._iobuf*, i8*, %struct.__crt_locale_pointers*, i8*) #2

attributes #0 = { nobuiltin noinline nounwind optnone "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-features"="+cx8,+mmx,+sse,+sse2,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { noinline nounwind optnone "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-features"="+cx8,+mmx,+sse,+sse2,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #2 = { "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-features"="+cx8,+mmx,+sse,+sse2,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #3 = { noreturn "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-features"="+cx8,+mmx,+sse,+sse2,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #4 = { nounwind readonly willreturn "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-features"="+cx8,+mmx,+sse,+sse2,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #5 = { nofree nosync nounwind willreturn }
attributes #6 = { noreturn }
attributes #7 = { nounwind readonly willreturn }

!llvm.module.flags = !{!0}
!llvm.ident = !{!1}

!0 = !{i32 1, !"wchar_size", i32 2}
!1 = !{!"clang version 12.0.0"}
!2 = distinct !{!2, !3}
!3 = !{!"llvm.loop.mustprogress"}
!4 = distinct !{!4, !3}
!5 = distinct !{!5, !3}

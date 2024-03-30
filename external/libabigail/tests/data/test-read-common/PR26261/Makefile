SRCS	= PR26261-obja.c PR26261-objb.c PR26261-main.c
EXE	= PR26261-exe
OBJS	= $(SRCS:.c=.o)
CFLAGS	= -Wall -g -gctf

all: $(EXE)

%.o: %.c
	$(CC) $(CFLAGS) -c $<

$(EXE): $(OBJS)
	$(CC) -fPIC $(OBJS) -o $@

cleanobjs:
	rm -rf $(OBJS)

cleanexe:
	rm $(EXE)

clean: cleanobjs
	rm -rf *~ 
